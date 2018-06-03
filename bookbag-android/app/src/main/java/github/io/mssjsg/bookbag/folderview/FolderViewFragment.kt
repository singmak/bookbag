package github.io.mssjsg.bookbag.folderview

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.databinding.DataBindingUtil
import android.net.Uri
import android.os.Bundle
import android.view.*
import github.io.mssjsg.bookbag.BookBagAppComponent
import github.io.mssjsg.bookbag.R
import github.io.mssjsg.bookbag.databinding.FragmentFolderviewBinding
import github.io.mssjsg.bookbag.folderselection.FolderSelectionFragment
import github.io.mssjsg.bookbag.folderselection.event.FolderSelectionEvent
import github.io.mssjsg.bookbag.list.ItemListContainerFragment
import github.io.mssjsg.bookbag.list.event.ItemClickEvent
import github.io.mssjsg.bookbag.list.event.ItemLongClickEvent
import github.io.mssjsg.bookbag.list.event.ItemToggleEvent
import github.io.mssjsg.bookbag.list.listitem.BookmarkListItem
import github.io.mssjsg.bookbag.util.extension.putFolderId
import github.io.mssjsg.bookbag.widget.SimpleConfirmDialogFragment
import github.io.mssjsg.bookbag.widget.SimpleInputDialogFragment

class FolderViewFragment: ItemListContainerFragment<FolderViewViewModel>(), ActionMode.Callback {
    private var actionMode: ActionMode? = null
    private lateinit var folderViewBinding: FragmentFolderviewBinding

    private var pendingNewBookmarkUrl: String? = null

    fun addBookmark(url: String) {
        try {
            viewModel.addBookmark(url)
        } catch (e: UninitializedPropertyAccessException) {
            pendingNewBookmarkUrl = url
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        folderViewBinding = DataBindingUtil.inflate(inflater, R.layout.fragment_folderview, container, false)
        return folderViewBinding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        pendingNewBookmarkUrl?.let { viewModel.addBookmark(it) }
        pendingNewBookmarkUrl = null

        folderViewBinding.layoutToolbar.toolbar.apply {
            inflateMenu(R.menu.menu_main)
            setOnMenuItemClickListener({
                when (it.itemId) {
                    R.id.item_new_folder -> {
                        SimpleInputDialogFragment.newInstance(CONFIRM_DIALOG_CREATE_NEW_FOLDER,
                                hint = getString(R.string.hint_folder_name),
                                title = getString(R.string.title_new_folder))
                                .show(childFragmentManager, TAG_CREATE_NEW_FOLDER)

                        true
                    }
                    else -> false
                }
            })
        }

        viewModel.localLiveBus.let {
            //init event
            it.subscribe(this, Observer {
                actionMode = folderViewBinding.layoutToolbar.toolbar.startActionMode(this)
            }, ItemLongClickEvent::class)

            it.subscribe(this, Observer {
                it?.let { onItemSelected(it.position) }
            }, ItemClickEvent::class)

            it.subscribe(this, Observer {
                if (viewModel.selectedItemCount == 0) actionMode?.finish()
            }, ItemToggleEvent::class)
        }

        viewModel.liveBus.let {
            it.subscribe(this, Observer {
                it?.apply {
                    when (requestId) {
                        CONFIRM_DIALOG_CREATE_NEW_FOLDER -> viewModel.addFolder(input)
                    }
                }
            }, SimpleInputDialogFragment.ConfirmEvent::class)

            it.subscribe(this, Observer {
                it?.apply {
                    when (requestId) {
                        CONFIRM_DIALOG_EXIT -> activity?.finish()
                    }
                }
            }, SimpleConfirmDialogFragment.ConfirmEvent::class)

            it.subscribe(this, Observer {
                it?.apply {
                    when (requestId) {
                        REQUEST_ID_MOVE_ITEMS -> {
                            if (confirmed) {
                                viewModel.moveSelectedItems(folderId)
                                viewModel.loadFolder(folderId)
                            }
                            viewModel.clearCachedSelectedItems()
                        }
                    }
                }
            }, FolderSelectionEvent::class)
        }

        viewModel.isInMultiSelectionMode = false
    }

    private fun onItemSelected(position: Int) {
        if (!viewModel.isInMultiSelectionMode) {
            viewModel.getListItem(position).let {
                when (it) {
                    is BookmarkListItem -> {
                        val i = Intent(Intent.ACTION_VIEW)
                        i.data = Uri.parse(it.url)
                        startActivity(i)
                    }
                }
            }
        }
    }

    override fun onCreateViewModel(): FolderViewViewModel {
        return ViewModelProviders.of(this, ViewModelFactory(getAppComponent()))
                .get(FolderViewViewModel::class.java)
    }

    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.item_delete -> {
                viewModel.deleteSelectedItems()
                mode?.finish()
                true
            }
            R.id.item_move -> {
                val selectedCount = viewModel.selectedItemCount
                val title = when (selectedCount) {
                    1 -> R.string.title_move_to_one
                    else -> R.string.title_move_to_other
                }.let { getString(it, selectedCount) }
                viewModel.cacheSelectedItems()
                actionMode?.finish()
                val fragment = FolderSelectionFragment.newInstance(
                        REQUEST_ID_MOVE_ITEMS,
                        viewModel.currentFolderId,
                        viewModel.getSelectedFolderIds().toTypedArray(),
                        title,
                        getString(R.string.btn_confirm_move)
                )
                navigationManager?.addToBackStack(fragment, TAG_CREATE_MOVE, R.anim.pop_enter_animation, R.anim.pop_exit_animation)
            }
            else -> false
        }

        return false
    }

    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        mode?.menuInflater?.inflate(R.menu.menu_main_action_mode, menu)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        viewModel.isInMultiSelectionMode = true
        return false
    }

    override fun onDestroyActionMode(mode: ActionMode?) {
        viewModel.isInMultiSelectionMode = false
    }

    override fun onBackPressed(): Boolean {
        if (viewModel.currentFolderId != null) {
            return super.onBackPressed()
        }

        SimpleConfirmDialogFragment.newInstance(CONFIRM_DIALOG_EXIT,
                title = getString(R.string.confirm_exit))
                .show(childFragmentManager, TAG_EXIT)
        return true
    }

    private class ViewModelFactory(val viewModelComponent: BookBagAppComponent) : ViewModelProvider.NewInstanceFactory() {

        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            return viewModelComponent.folderViewComponent().let { component ->
                component.provideFolderViewModel().apply { folderViewComponent = component } as T
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val CONFIRM_DIALOG_CREATE_NEW_FOLDER = "github.io.mssjsg.bookbag.main.CONFIRM_DIALOG_CREATE_NEW_FOLDER"
        private const val CONFIRM_DIALOG_EXIT = "github.io.mssjsg.bookbag.main.CONFIRM_DIALOG_EXIT"
        private const val TAG_CREATE_NEW_FOLDER = "github.io.mssjsg.bookbag.main.TAG_CREATE_NEW_FOLDER"
        private const val TAG_CREATE_MOVE = "github.io.mssjsg.bookbag.main.TAG_MOVE"
        private const val TAG_EXIT = "github.io.mssjsg.bookbag.main.TAG_EXIT"
        private const val REQUEST_ID_MOVE_ITEMS = 1000

        fun newInstance(folderId: String? = null): FolderViewFragment {
            val fragment = FolderViewFragment()
            fragment.arguments = Bundle().apply {
                putFolderId(folderId)
            }
            return fragment
        }
    }

    override fun isSignInRequired(): Boolean {
        return true
    }
}