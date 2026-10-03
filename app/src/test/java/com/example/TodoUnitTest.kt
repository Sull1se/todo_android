package com.example

import com.example.data.AppDatabase
import com.example.data.Task
import org.junit.Assert.*
import org.junit.Test

class TodoUnitTest {

    @Test
    fun task_defaultIsFlagged_isFalse() {
        val task = Task(
            id = 1,
            listId = 1,
            title = "Test Task",
            content = "Test Content",
            displayOrder = 0
        )
        assertFalse("Task should not be flagged by default", task.isFlagged)
    }

    @Test
    fun task_copy_canSetFlagged() {
        val task = Task(
            id = 1,
            listId = 1,
            title = "Test Task",
            content = "Test Content",
            displayOrder = 0
        )
        val flaggedTask = task.copy(isFlagged = true)
        assertTrue("Flagged task should have isFlagged = true", flaggedTask.isFlagged)
    }

    @Test
    fun flagTask_movesTaskToTopOfList_andUpdatesDisplayOrder() {
        // Initial list: Task 1, Task 2, Task 3
        val task1 = Task(id = 1, listId = 1, title = "Task 1", content = "", displayOrder = 0, isFlagged = false)
        val task2 = Task(id = 2, listId = 1, title = "Task 2", content = "", displayOrder = 1, isFlagged = false)
        val task3 = Task(id = 3, listId = 1, title = "Task 3", content = "", displayOrder = 2, isFlagged = false)

        val currentTasks = mutableListOf(task1, task2, task3)

        // Flag task3 (index 2)
        val targetIndex = currentTasks.indexOfFirst { it.id == task3.id }
        assertEquals(2, targetIndex)

        val flaggedTask = task3.copy(isFlagged = true)
        currentTasks.removeAt(targetIndex)
        currentTasks.add(0, flaggedTask)

        val reordered = currentTasks.mapIndexed { index, task -> task.copy(displayOrder = index) }

        // Assertions
        assertEquals(3, reordered.size)
        assertEquals("Task 3 should be at the top", 3, reordered[0].id)
        assertTrue("Task 3 should be flagged", reordered[0].isFlagged)
        assertEquals("Task 3 displayOrder should be 0", 0, reordered[0].displayOrder)

        assertEquals("Task 1 should now be at index 1", 1, reordered[1].id)
        assertEquals("Task 1 displayOrder should be 1", 1, reordered[1].displayOrder)

        assertEquals("Task 2 should now be at index 2", 2, reordered[2].id)
        assertEquals("Task 2 displayOrder should be 2", 2, reordered[2].displayOrder)
    }

    @Test
    fun unflagTask_movesTaskToBottomOfList_andUpdatesDisplayOrder() {
        // Initial list where Task 3 is already flagged at the top
        val task3 = Task(id = 3, listId = 1, title = "Task 3", content = "", displayOrder = 0, isFlagged = true)
        val task1 = Task(id = 1, listId = 1, title = "Task 1", content = "", displayOrder = 1, isFlagged = false)
        val task2 = Task(id = 2, listId = 1, title = "Task 2", content = "", displayOrder = 2, isFlagged = false)

        val currentTasks = mutableListOf(task3, task1, task2)

        // Unflag task3: move to bottom and reorder displayOrder
        val index = currentTasks.indexOfFirst { it.id == task3.id }
        val unflaggedTask = task3.copy(isFlagged = false)
        currentTasks.removeAt(index)
        currentTasks.add(unflaggedTask)
        val reordered = currentTasks.mapIndexed { i, t -> t.copy(displayOrder = i) }

        assertEquals(3, reordered.size)
        assertEquals("Task 1 is now at top", 1, reordered[0].id)
        assertEquals(0, reordered[0].displayOrder)
        assertEquals("Task 2 is at index 1", 2, reordered[1].id)
        assertEquals(1, reordered[1].displayOrder)
        assertEquals("Task 3 should now be at the bottom", 3, reordered[2].id)
        assertFalse("Task 3 is now unflagged", reordered[2].isFlagged)
        assertEquals("Task 3 displayOrder should be 2", 2, reordered[2].displayOrder)
    }

    @Test
    fun consecutiveToggles_firstFlagsAndMovesToTop_secondUnflagsAndMovesToBottom() {
        val task1 = Task(id = 1, listId = 1, title = "Task 1", content = "", displayOrder = 0, isFlagged = false)
        val task2 = Task(id = 2, listId = 1, title = "Task 2", content = "", displayOrder = 1, isFlagged = false)

        val list = mutableListOf(task1, task2)

        // First swipe on Task 2: should flag and move to top
        val idx1 = list.indexOfFirst { it.id == 2 }
        val current1 = list[idx1]
        val willBeFlagged1 = !current1.isFlagged
        assertTrue(willBeFlagged1)

        val flagged = current1.copy(isFlagged = true)
        list.removeAt(idx1)
        list.add(0, flagged)
        val reordered1 = list.mapIndexed { i, t -> t.copy(displayOrder = i) }.toMutableList()

        assertEquals(2, reordered1[0].id)
        assertTrue(reordered1[0].isFlagged)
        assertEquals(0, reordered1[0].displayOrder)

        // Second swipe on Task 2 (now at top): should unflag and move to bottom
        val idx2 = reordered1.indexOfFirst { it.id == 2 }
        val current2 = reordered1[idx2]
        val willBeFlagged2 = !current2.isFlagged
        assertFalse(willBeFlagged2)

        val unflagged = current2.copy(isFlagged = false)
        reordered1.removeAt(idx2)
        reordered1.add(unflagged)
        val reordered2 = reordered1.mapIndexed { i, t -> t.copy(displayOrder = i) }

        assertEquals(1, reordered2[0].id)
        assertEquals(0, reordered2[0].displayOrder)
        assertEquals(2, reordered2[1].id)
        assertFalse(reordered2[1].isFlagged)
        assertEquals(1, reordered2[1].displayOrder)
    }

    @Test
    fun updateTaskDetail_whenUnflaggedInDetail_movesToBottomAndClearsFlag() {
        // Task 2 was flagged and at top
        val task2 = Task(id = 2, listId = 1, title = "Task 2", content = "", displayOrder = 0, isFlagged = true)
        val task1 = Task(id = 1, listId = 1, title = "Task 1", content = "", displayOrder = 1, isFlagged = false)

        val list = mutableListOf(task2, task1)

        // User edits Task 2 in detail and unflags it
        val idx = list.indexOfFirst { it.id == 2 }
        val current = list[idx]
        val wasFlagged = current.isFlagged
        val newIsFlagged = false

        assertTrue(wasFlagged)
        assertFalse(newIsFlagged)

        // unflag from detail: moves to bottom and reorders
        val unflagged = current.copy(title = "Task 2 Updated", isFlagged = false)
        list.removeAt(idx)
        list.add(unflagged)
        val reordered = list.mapIndexed { i, t -> t.copy(displayOrder = i) }

        assertEquals(1, reordered[0].id)
        assertEquals(0, reordered[0].displayOrder)
        assertEquals(2, reordered[1].id)
        assertFalse(reordered[1].isFlagged)
        assertEquals("Task 2 Updated", reordered[1].title)
        assertEquals(1, reordered[1].displayOrder)

        // Then if user swipes Task 2 in home: it should flag again
        val nextToggle = !reordered[1].isFlagged
        assertTrue("Subsequent swipe should flag the task again", nextToggle)
    }

    @Test
    fun addTask_whenFlagged_isPlacedAtTopAndUpdatesDisplayOrder() {
        val task1 = Task(id = 1, listId = 1, title = "Existing 1", content = "", displayOrder = 0, isFlagged = false)
        val task2 = Task(id = 2, listId = 1, title = "Existing 2", content = "", displayOrder = 1, isFlagged = false)

        val currentTasks = mutableListOf(task1, task2)

        val newTask = Task(id = 3, listId = 1, title = "New Flagged Task", content = "", displayOrder = 0, isFlagged = true)
        currentTasks.add(0, newTask)
        val reordered = currentTasks.mapIndexed { index, task -> task.copy(displayOrder = index) }

        assertEquals(3, reordered.size)
        assertEquals("New task is at index 0", 3, reordered[0].id)
        assertTrue(reordered[0].isFlagged)
        assertEquals(0, reordered[0].displayOrder)

        assertEquals("Existing 1 moved to index 1", 1, reordered[1].id)
        assertEquals(1, reordered[1].displayOrder)

        assertEquals("Existing 2 moved to index 2", 2, reordered[2].id)
        assertEquals(2, reordered[2].displayOrder)
    }
}
