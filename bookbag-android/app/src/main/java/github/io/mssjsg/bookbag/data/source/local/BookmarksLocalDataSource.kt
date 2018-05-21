package github.io.mssjsg.bookbag.data.source.local

import github.io.mssjsg.bookbag.data.Bookmark
import github.io.mssjsg.bookbag.data.source.BookmarksDataSource
import github.io.mssjsg.bookbag.util.executor.qualifier.DiskIoExecutor
import io.reactivex.Flowable
import java.util.concurrent.Executor
import javax.inject.Inject

/**
 * Created by Sing on 27/3/2018.
 */
class BookmarksLocalDataSource @Inject constructor(@DiskIoExecutor val executor: Executor,
                                                   val bookmarksDao: BookmarksDao) : BookmarksDataSource {

    fun updateBookmarkPreview(bookmarkUrl: String, imageUrl: String, title: String) {
        executor.execute {
            val bookmark = bookmarksDao.getBookmark(bookmarkUrl)
            bookmarksDao.updateBookmark(bookmark.copy(imageUrl = imageUrl, name = title))
        }
    }

    override fun getDirtyBookmarks(): Flowable<List<Bookmark>> {
        return bookmarksDao.getDirtyBookmarks()
    }

    override fun moveBookmark(url: String, folderId: String?) {
        executor.execute {
            bookmarksDao.moveBookmark(url, folderId)
        }
    }

    override fun getBookmarks(folderId: String?): Flowable<List<Bookmark>> {
        return folderId?.let { bookmarksDao.getBookmarksByFolderId(folderId) }
                ?: bookmarksDao.getHomeBookmarks()
    }

    override fun deleteBookmarks(bookmarkUrls: List<String>) {
        executor.execute {
            for (url in bookmarkUrls) {
                bookmarksDao.deleteBookmarkByUrl(url)
            }
        }
    }

    override fun saveBookmark(bookmark: Bookmark) {
        executor.execute {
            bookmarksDao.insertBookmark(bookmark)
        }
    }

    override fun updateBookmark(bookmark: Bookmark) {
        executor.execute { bookmarksDao.updateBookmark(bookmark) }
    }
}