<template>
  <el-dialog :model-value="modelValue" :title="material ? '修改资料并重新提交' : '上传学习资料'" width="min(600px, 94vw)" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy" @update:model-value="close">
    <el-alert v-if="material?.reject_reason" :title="'驳回原因：' + material.reject_reason" type="warning" :closable="false" show-icon />
    <el-form label-position="top" @submit.prevent="submit">
      <el-form-item label="资料名称"><el-input v-model="form.material_name" maxlength="200" show-word-limit :disabled="busy" /></el-form-item>
      <el-form-item :label="material ? '替换文件（不选则保留原文件）' : '选择文件'">
        <input ref="fileInput" type="file" :disabled="busy" accept=".pdf,.doc,.docx,.xls,.xlsx,.csv,.ppt,.pptx,.png,.jpg,.jpeg,.gif,.webp,.zip,.rar,.7z" @change="chooseFile" />
        <p class="file-hint">单文件不超过 50MB。{{ material ? `当前版本 V${material.revision}，原文件：${material.original_file_name || material.material_name}` : '支持文档、图片和压缩包。' }}</p>
      </el-form-item>
      <div class="editor-grid">
        <el-form-item label="关联课程"><el-select v-model="form.course_id" filterable clearable placeholder="可不关联"><el-option v-for="c in courses" :key="c.course_id" :label="c.course_name" :value="c.course_id" /></el-select></el-form-item>
        <el-form-item label="文件类型"><el-select v-model="form.material_type"><el-option v-for="(label, id) in MATERIAL_TYPE_LABEL" :key="id" :label="label" :value="Number(id)" /></el-select></el-form-item>
      </div>
      <el-form-item label="下载所需积分"><el-input-number v-model="form.download_score" :min="0" :max="1000" :precision="0" :disabled="busy" /></el-form-item>
      <el-form-item label="关键词"><el-input v-model="form.keyword" maxlength="100" show-word-limit :disabled="busy" /></el-form-item>
      <el-form-item label="资料说明"><el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" show-word-limit :disabled="busy" /></el-form-item>
    </el-form>
    <p v-if="error" class="editor-error" role="alert">{{ error }}</p>
    <template #footer><el-button :disabled="busy" @click="close(false)">取消</el-button><el-button type="primary" :loading="busy" @click="submit">{{ material ? '保存修改并提交审核' : '提交上传' }}</el-button></template>
  </el-dialog>
</template>
<script setup>
import { ref, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { MATERIAL_TYPE_LABEL } from '@/utils/constants'
import * as api from '@/api/material'
import { useRequestGate } from '@/composables/useListQuery'
const props = defineProps({ modelValue: Boolean, material: { type: Object, default: null }, courses: { type: Array, default: () => [] } })
const emit = defineEmits(['update:modelValue', 'saved'])
const form = reactive({}), file = ref(null), fileInput = ref(null), busy = ref(false), error = ref('')
const gate = useRequestGate(() => { file.value = null; busy.value = false; emit('update:modelValue', false) })
watch(() => props.modelValue, open => {
  if (!open) return
  error.value = ''; file.value = null
  if (fileInput.value) fileInput.value.value = ''
  Object.assign(form, { material_name: '', material_type: 1, course_id: null, download_score: 0, keyword: '', description: '', ...props.material })
})
function close(open) { if (!busy.value) emit('update:modelValue', open) }
function chooseFile(event) {
  const selected = event.target.files?.[0]
  file.value = null
  if (!selected) return
  if (selected.size > 50 * 1024 * 1024) { error.value = '文件不能超过 50MB'; event.target.value = ''; return }
  file.value = selected; error.value = ''
  if (!form.material_name) form.material_name = selected.name.replace(/\.[^.]+$/, '')
  const ext = selected.name.split('.').pop().toLowerCase()
  form.material_type = ext === 'pdf' ? 1 : ['doc','docx'].includes(ext) ? 2 : ['xls','xlsx','csv'].includes(ext) ? 3 : ['ppt','pptx'].includes(ext) ? 4 : ['png','jpg','jpeg','gif','webp'].includes(ext) ? 5 : 6
}
async function submit() {
  if (busy.value) return
  if (!form.material_name?.trim()) { error.value = '请填写资料名称'; return }
  if (form.material_name.trim().length > 200 || (form.keyword || '').length > 100 || (form.description || '').length > 500) { error.value = '请检查字数：名称最多200字，关键词100字，说明500字'; return }
  if (!Number.isInteger(form.download_score) || form.download_score < 0 || form.download_score > 1000) { error.value = '下载积分需为0至1000的整数'; return }
  if (!props.material && !file.value) { error.value = '请选择文件'; return }
  const ticket = gate.begin(), editing = props.material
  busy.value = true; error.value = ''
  try {
    const data = { ...form, material_name: form.material_name.trim(), file: file.value, course_id: form.course_id || null }
    const res = editing ? await api.resubmitMaterial(editing.material_id, data) : await api.uploadMaterial(data)
    if (!gate.valid(ticket)) return
    ElMessage.success(editing ? '修改已提交审核' : '上传成功，审核通过后发放奖励')
    emit('update:modelValue', false); emit('saved', res.data)
  } catch (err) { if (gate.valid(ticket) && !err.sessionChanged) error.value = err.message || '提交失败，修改已保留，请重试' }
  finally { if (gate.valid(ticket)) busy.value = false }
}
</script>
<style scoped>
.editor-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.file-hint { color: var(--color-text-secondary); font-size: 13px; overflow-wrap: anywhere; }
.editor-error { color: var(--color-danger); } input[type=file] { max-width: 100%; }
@media (max-width: 600px) { .editor-grid { grid-template-columns: 1fr; gap: 0; } }
</style>
