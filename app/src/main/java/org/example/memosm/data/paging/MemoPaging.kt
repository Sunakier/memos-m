package org.example.memosm.data.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.paging.filter
import androidx.paging.map
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.example.memosm.api.GsonProvider
import org.example.memosm.api.MemoOrderBy
import org.example.memosm.api.MemoSearchFilter
import org.example.memosm.api.buildMemoSearchFilter
import org.example.memosm.api.resolveMemoOrderBy
import org.example.memosm.data.backup.BackupCoordinator
import org.example.memosm.data.cache.CacheListType
import org.example.memosm.data.cache.CachedMemo
import org.example.memosm.data.cache.MemoCacheRepository
import org.example.memosm.model.Memo
import org.example.memosm.model.MemoState
import org.example.memosm.model.User
import org.example.memosm.model.Visibility
import org.example.memosm.viewmodel.AccountContext
import java.io.IOException

private const val SHORTCUT_TAG_LITERAL = """(?:"[^"\\\r\n]+"|'[^'\\\r\n]+')"""
private const val SHORTCUT_TAG_GROUP = """tag\s+in\s+\[\s*$SHORTCUT_TAG_LITERAL(?:\s*,\s*$SHORTCUT_TAG_LITERAL)*\s*]"""
private val shortcutTagFilter = Regex("""\s*$SHORTCUT_TAG_GROUP(?:\s*&&\s*$SHORTCUT_TAG_GROUP)*\s*""")
private val shortcutTagGroup = Regex(SHORTCUT_TAG_GROUP)
private val shortcutTagLiteral = Regex(SHORTCUT_TAG_LITERAL)
private val markdownHashtag = Regex("""#[^\s#]+""")

internal fun isLocalShortcutTagFilter(filter: String): Boolean = shortcutTagFilter.matches(filter)

/** A request captures its account, parent, filter and ordering for its entire lifetime. */
data class MemoQuery(
    @SerializedName("listType") val listType: CacheListType,
    @SerializedName("parentName") val parentName: String? = null,
    @SerializedName("search") val search: MemoSearchFilter? = null,
    @SerializedName("explore") val explore: Boolean = false,
    @SerializedName("hashtag") val hashtag: String? = null,
    @SerializedName("serverFilter") val serverFilter: String? = null,
    @SerializedName("order") val order: MemoOrderBy = MemoOrderBy.NEWEST,
) {
    // A shortcut can reuse the personal cache only if its entire CEL filter is understood.
    @Transient
    internal val shortcutTagGroups =
        serverFilter?.takeIf(::isLocalShortcutTagFilter)?.let { filter ->
            shortcutTagGroup.findAll(filter).map { group ->
                shortcutTagLiteral.findAll(group.value).map { it.value.substring(1, it.value.lastIndex) }.toSet()
            }.toList()
        }

    val key: String get() =
        if (search == null && hashtag == null && serverFilter.isNullOrBlank()) {
            listType.name + parentName?.let { ":$it" }.orEmpty()
        } else {
            listType.name + ":" + GsonProvider.gson.toJson(copy(search = search?.copy(tags = search.tags.toSortedSet())))
        }
}

fun matchesLocalQuery(
    memo: Memo,
    query: MemoQuery,
    user: User?,
): Boolean {
    val tagGroups = query.shortcutTagGroups
    if (!query.serverFilter.isNullOrBlank() && tagGroups == null) return false
    val tags = memo.tags.orEmpty()
    if (tagGroups != null && tagGroups.any { group -> group.none(tags::contains) }) return false
    if (query.explore && memo.visibility !in setOf(Visibility.PUBLIC, Visibility.PROTECTED)) return false
    if (query.listType == CacheListType.SEARCH && memo.state == MemoState.ARCHIVED) return false
    if (query.listType == CacheListType.SEARCH && !query.explore && memo.creator != user?.name) return false
    val criteria = query.search
    query.hashtag?.removePrefix("#")?.let { tag ->
        val matches = memo.tags?.contains(tag) ?: markdownHashtag.findAll(memo.content).any { it.value == "#$tag" }
        if (!matches) return false
    }
    if (criteria == null) return true
    if (!memo.content.contains(criteria.query, ignoreCase = true)) return false
    if (!tags.containsAll(criteria.tags)) return false
    val time = memo.createTime?.toEpochMilliseconds() ?: 0L
    if (criteria.startDateMillis?.let { time < it } == true) return false
    if (criteria.endDateMillis?.let { time >= it + 86_400_000L } == true) return false
    return true
}

/** Offline append ends remote loading; the Room source can still page through every cached row. */
@OptIn(ExperimentalPagingApi::class)
internal fun offlineLoadResult(loadType: LoadType): RemoteMediator.MediatorResult =
    if (loadType == LoadType.APPEND) {
        RemoteMediator.MediatorResult.Success(true)
    } else {
        RemoteMediator.MediatorResult.Error(IOException("Server is unavailable"))
    }

@OptIn(ExperimentalPagingApi::class)
suspend fun memoPages(
    account: AccountContext,
    repository: MemoCacheRepository,
    query: MemoQuery,
    user: User?,
    pageSize: Int,
    canFetch: () -> Boolean,
    isCurrent: () -> Boolean,
): Flow<PagingData<Memo>> {
    val storageGeneration = BackupCoordinator.generation

    fun current() = isCurrent() && BackupCoordinator.generation == storageGeneration
    val hasLocalFilter =
        if (query.serverFilter.isNullOrBlank()) query.search != null || query.hashtag != null else query.shortcutTagGroups != null
    val localSource =
        if (!hasLocalFilter) {
            null
        } else if (query.listType == CacheListType.SEARCH) {
            if (query.explore) CacheListType.EXPLORE else CacheListType.USER
        } else {
            query.listType
        }
    return Pager(
        config = PagingConfig(pageSize = pageSize.coerceAtLeast(10), enablePlaceholders = false),
        remoteMediator =
            object : RemoteMediator<Int, CachedMemo>() {
                private val tokens = mutableSetOf<String>()

                override suspend fun initialize() =
                    if (canFetch()) {
                        InitializeAction.LAUNCH_INITIAL_REFRESH
                    } else {
                        InitializeAction.SKIP_INITIAL_REFRESH
                    }

                override suspend fun load(
                    loadType: LoadType,
                    state: PagingState<Int, CachedMemo>,
                ): MediatorResult {
                    if (loadType == LoadType.PREPEND) return MediatorResult.Success(true)
                    if (!current()) throw CancellationException("Account changed")
                    return try {
                        val previous = repository.getQuery(account.account.id, query.key)
                        val token = if (loadType == LoadType.REFRESH) null else previous?.nextPageToken
                        if (loadType == LoadType.APPEND && previous?.complete == true && token == null) {
                            return MediatorResult.Success(true)
                        }
                        if (!canFetch()) return offlineLoadResult(loadType)
                        if (loadType == LoadType.REFRESH) tokens.clear()
                        val generation =
                            if (loadType == LoadType.REFRESH) {
                                BackupCoordinator.withStorageLock {
                                    if (!current()) throw CancellationException("Account storage changed")
                                    repository.beginRefresh(account.account.id, query.key, query.listType, query.parentName)
                                }
                            } else {
                                previous?.generation
                            }
                        val api = account.api
                        val response =
                            if (query.listType == CacheListType.COMMENT) {
                                val parent = query.parentName ?: return MediatorResult.Success(true)
                                val remoteParent =
                                    repository.resolveServerName(account.account.id, parent)
                                        ?: throw IOException("Parent memo is awaiting sync")
                                val page =
                                    api.listMemoComments(
                                        remoteParent,
                                        state.config.pageSize,
                                        token,
                                        api.resolveMemoOrderBy(MemoOrderBy.OLDEST),
                                    )
                                (page.memos ?: throw IOException("Server omitted the memo collection")) to page.nextPageToken
                            } else {
                                val creator =
                                    if (query.listType == CacheListType.USER || query.listType == CacheListType.ARCHIVED ||
                                        query.listType == CacheListType.SEARCH && !query.explore
                                    ) {
                                        api.buildMemoCreatorFilter(user) ?: throw IOException("User identity is unavailable")
                                    } else {
                                        null
                                    }
                                val filter =
                                    listOfNotNull(
                                        creator,
                                        if (query.listType == CacheListType.EXPLORE ||
                                            query.explore
                                        ) {
                                            "visibility in ['PUBLIC', 'PROTECTED']"
                                        } else {
                                            null
                                        },
                                        query.hashtag?.let { "tag in [${GsonProvider.gson.toJson(it.removePrefix("#"))}]" },
                                        query.serverFilter,
                                        query.search?.let(api::buildMemoSearchFilter),
                                    ).filter { it.isNotBlank() }
                                        .joinToString(" && ")
                                        .ifBlank { null }
                                val page =
                                    api.listMemos(
                                        state.config.pageSize,
                                        token,
                                        state = if (query.listType == CacheListType.ARCHIVED) "ARCHIVED" else null,
                                        orderBy =
                                            api.resolveMemoOrderBy(
                                                if (query.listType ==
                                                    CacheListType.USER
                                                ) {
                                                    MemoOrderBy.PINNED_DESC
                                                } else {
                                                    query.order
                                                },
                                            ),
                                        filter = filter,
                                    )
                                (page.memos ?: throw IOException("Server omitted the memo collection")) to page.nextPageToken
                            }
                        if (!current()) throw CancellationException("Account changed")
                        val next = response.second?.takeIf { it.isNotBlank() }
                        token?.let(tokens::add)
                        check(next == null || tokens.add(next)) { "Server repeated a pagination token" }
                        BackupCoordinator.withStorageLock {
                            if (!current()) throw CancellationException("Account storage changed")
                            repository.ingestPage(
                                account.account.id,
                                query.key,
                                query.listType,
                                response.first,
                                query.parentName,
                                nextPageToken = next,
                                refresh = loadType == LoadType.REFRESH,
                                expectedGeneration = generation,
                                expectedPageToken = token,
                            )
                        }
                        MediatorResult.Success(next == null)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        MediatorResult.Error(error)
                    }
                }
            },
        pagingSourceFactory = {
            repository.pagingSource(
                account.account.id,
                query.key,
                when (query.listType) {
                    CacheListType.USER -> MemoOrderBy.PINNED_DESC
                    CacheListType.COMMENT -> MemoOrderBy.OLDEST
                    else -> query.order
                },
                sourceListType = localSource,
            )
        },
    ).flow.map { page ->
        val memos = page.map { requireNotNull(it.toMemo()) { "Invalid cached memo" } }
        if (localSource == null) memos else memos.filter { matchesLocalQuery(it, query, user) }
    }
}
