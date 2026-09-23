package com.gdufs.schedule.data.model

import androidx.room.Embedded
import androidx.room.Relation

/**
 * 课程与其全部上课安排的关系对象,用于 UI 卡片展示。
 */
data class CourseWithOccurrences(
    @Embedded val course: Course,
    @Relation(
        parentColumn = "id",
        entityColumn = "courseId",
    )
    val occurrences: List<CourseOccurrence>,
)