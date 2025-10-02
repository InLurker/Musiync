package com.metrolist.music.presentation.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import com.metrolist.music.common.models.LibrarySnapshotSerializer
import com.metrolist.music.datastore.LibrarySnapshotProto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

private const val LIBRARY_SNAPSHOT_FILE = "library_snapshot.pb"

private val Context.librarySnapshotDataStore: DataStore<LibrarySnapshotProto> by dataStore(
    fileName = LIBRARY_SNAPSHOT_FILE,
    serializer = LibrarySnapshotSerializer
)

@Singleton
class DataStoreRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    val librarySnapshots: Flow<LibrarySnapshotProto>
        get() = context.librarySnapshotDataStore.data

    suspend fun persistLibrarySnapshot(snapshot: LibrarySnapshotProto) {
        context.librarySnapshotDataStore.updateData { snapshot }
    }
}
