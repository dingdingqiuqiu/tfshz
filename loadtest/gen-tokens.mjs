import crypto from 'node:crypto';
import fs from 'node:fs';

/**
 * 生成压测用的 JWT（每个用户一个 token，模拟真实并发用户）。
 *
 * 用法：
 *   node gen-tokens.mjs --count 200 --start 940001 --out tokens.csv
 *   node gen-tokens.mjs --count 200 --start 940001 --secret "<后端 JWT_SECRET>" > tokens.csv
 *
 * 注意：
 *   1. secret 必须与后端 `tfsh.jwt.secret`（环境变量 JWT_SECRET）一致，否则全部 401；
 *   2. **务必用 --out 写文件**：Windows PowerShell 的 `>` 重定向默认写 UTF-16LE，
 *      JMeter 按 UTF-8 读出来就是乱码 token，表现为"100% 401"（这个坑踩过）。
 */

const DEFAULT_SECRET = 'RFFjFMAArtLeIWvzItoC+VdA4y8oXDW7Hb0I5+KXuqEeNB4BY4ungvy9s0Ap5tRY';

const args = process.argv.slice(2);
function arg(name, fallback) {
  const index = args.indexOf(`--${name}`);
  return index >= 0 && args[index + 1] ? args[index + 1] : fallback;
}

const count = Number(arg('count', 200));
const start = Number(arg('start', 940001));
const secret = arg('secret', process.env.JWT_SECRET || DEFAULT_SECRET);
const ttlSeconds = Number(arg('ttl', 7200));
const outFile = arg('out', '');

const b64 = (obj) => Buffer.from(JSON.stringify(obj)).toString('base64url');

function token(userId) {
  const now = Math.floor(Date.now() / 1000);
  const header = b64({ alg: 'HS256' });
  const payload = b64({
    iss: 'tfshz',
    sub: String(userId),
    jti: crypto.randomUUID(),
    nickName: `压测${userId}`,
    icon: '/imgs/icons/kkjtbcr.jpg',
    type: 'access',
    iat: now,
    exp: now + ttlSeconds,
  });
  const signature = crypto.createHmac('sha256', secret).update(`${header}.${payload}`).digest('base64url');
  return `${header}.${payload}.${signature}`;
}

// CSV 表头 + 每行一个用户；JMeter 的 CSV Data Set 配置会跳过表头
const lines = ['token,user'];
for (let i = 0; i < count; i++) {
  const userId = start + i;
  lines.push(`${token(userId)},${userId}`);
}
const content = `${lines.join('\n')}\n`;

if (outFile) {
  fs.writeFileSync(outFile, content, 'utf8');
  console.error(`已写入 ${outFile}（${count} 个用户，userId ${start} ~ ${start + count - 1}）`);
} else {
  fs.writeSync(1, content);
}
