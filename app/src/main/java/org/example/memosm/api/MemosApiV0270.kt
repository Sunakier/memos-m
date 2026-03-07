package org.example.memosm.api

import org.example.memosm.model.GetCurrentUserResponse
import retrofit2.http.GET

interface MemosApiV0270 : MemosApiV0260 {

    // Inherits getCurrentUser from MemosApiV0260
    // Inherits signIn from MemosApiV0260
    // Note: v0.27 uses the same auth/me endpoint as 0.26
}
