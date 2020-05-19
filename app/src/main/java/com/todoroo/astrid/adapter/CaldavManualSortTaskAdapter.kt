package com.todoroo.astrid.adapter

import com.todoroo.astrid.dao.TaskDao
import org.tasks.data.CaldavDao
import org.tasks.data.TaskContainer

open class CaldavManualSortTaskAdapter internal constructor(private val taskDao: TaskDao, private val caldavDao: CaldavDao) : TaskAdapter() {
    override fun canMove(source: TaskContainer, from: Int, target: TaskContainer, to: Int) = !taskIsChild(source, to)

    override fun maxIndent(previousPosition: Int, task: TaskContainer) = getTask(previousPosition).getIndent() + 1

    override fun minIndent(nextPosition: Int, task: TaskContainer): Int {
        (nextPosition until count).forEach {
            if (!taskIsChild(task, it)) {
                return getTask(it).indent
            }
        }
        return 0
    }

    override fun supportsParentingOrManualSort() = true

    override fun supportsManualSorting() = true

    override fun moved(from: Int, to: Int, indent: Int) {
        val task = getTask(from)
        val previous = if (to > 0) getTask(to - 1) else null
        val newParent = findNewParent(indent, to)

        if (newParent == task.parent && from == to) {
            return
        }

        if (newParent != task.parent) {
            changeParent(task, newParent)
        }
        if (from != to) {
            val newPosition = when {
                previous == null -> 1
                indent > previous.getIndent() -> 1
                indent == previous.getIndent() -> previous.caldavSortOrder + 1
                else -> getTask((to - 1 downTo 0).find { getTask(it).indent == indent }!!).caldavSortOrder + 1
            }
            caldavDao.move(task, newParent, newPosition)
        }

        taskDao.touch(task.id)
    }

    internal fun findNewParent(indent: Int, to: Int): Long {
        val previous = if (to > 0) getTask(to - 1) else null
        return when {
            indent == 0 || previous == null -> 0
            indent == previous.getIndent() -> previous.parent
            indent > previous.getIndent() -> previous.id
            else -> {
                var newParent = previous.parent
                var currentIndex = to
                for (i in 0 until previous.getIndent() - indent) {
                    var thisParent = newParent
                    while (newParent == thisParent) {
                        thisParent = getTask(--currentIndex).parent
                    }
                    newParent = thisParent
                }
                newParent
            }
        }
    }

    internal fun changeParent(task: TaskContainer, newParent: Long) {
        val caldavTask = task.getCaldavTask()
        if (newParent == 0L) {
            caldavTask.cd_remote_parent = ""
            task.parent = 0
        } else {
            val parentTask = caldavDao.getTask(newParent) ?: return
            caldavTask.cd_remote_parent = parentTask.remoteId
            task.parent = newParent
        }
        caldavDao.updateParent(caldavTask)
        taskDao.save(task.getTask(), null)
    }

    private fun taskIsChild(source: TaskContainer, destinationIndex: Int): Boolean {
        (destinationIndex downTo 0).forEach {
            when (getTask(it).parent) {
                0L -> return false
                source.parent -> return false
                source.id -> return true
            }
        }
        return false
    }
}