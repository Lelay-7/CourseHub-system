import { execFileSync } from 'node:child_process'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createHash } from 'node:crypto'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
const common = resolve(root, execFileSync('git', ['rev-parse', '--git-common-dir'], { cwd: root, encoding: 'utf8', windowsHide: true }).trim())
const repositoryKey = createHash('sha256').update(common.toLowerCase()).digest('hex').slice(0, 12)
const home = resolve(process.env.PROGRAMDATA || 'C:/ProgramData', 'CourseHubLocalDemo', repositoryKey)
const override = process.env.COURSEHUB_DEMO_RUNTIME
if (override && !/^rehearsals[\\/][a-f0-9]{32}[\\/]runtime$/.test((await import('node:path')).relative(home, resolve(override)))) throw new Error('Unexpected rehearsal runtime path')
export const previewRuntime = override ? resolve(override) : resolve(home, 'runtime')
