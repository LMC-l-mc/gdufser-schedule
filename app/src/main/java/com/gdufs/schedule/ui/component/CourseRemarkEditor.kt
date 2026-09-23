package com.gdufs.schedule.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gdufs.schedule.R
import com.gdufs.schedule.data.model.Course

/**
 * 课程备注编辑弹窗(Compose ModalBottomSheet):
 * - 总高度约为可用屏幕高度的 40%;
 * - 顶部为课程名称与"课程备注"标题,中间为多行输入框,底部固定取消/保存;
 * - 键盘弹出时经 [Modifier.imePadding] 保持输入框与保存按钮可见可点;
 * - 保存将文本(去除首尾空白)经 [onSave] 交给上层 ViewModel 写库,空文本表示清除备注;
 * - 组件自身不访问数据库,仅维护输入草稿状态。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseRemarkEditor(
    course: Course,
    initialRemark: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    // 以课程 id 为 key,切换课程时重置草稿
    var text by remember(course.id) { mutableStateOf(initialRemark) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.4f)
                .imePadding()
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = course.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.course_remark_editor_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                minLines = 4,
                placeholder = { Text(stringResource(R.string.course_remark_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(onClick = { onSave(text.trim()) }) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, name = "备注编辑弹窗")
@Composable
private fun CourseRemarkEditorPreview() {
    CourseRemarkEditor(
        course = Course(courseTableId = 1L, name = "高等数学", remark = ""),
        initialRemark = "记得带教材",
        onDismiss = {},
        onSave = {},
    )
}