// 웹 클라이언트의 proto.js가 Java(common 패키지)와 같은 결과(상태 해석, 숫자 계산)를 내는지 비교한다.
//   java ... stim.davincicode.server.DeductionDump states.jsonl
//   node src/test/web/parity.mjs states.jsonl
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve, dirname } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const protoPath = resolve(here, '../../main/resources/stim/davincicode/web/proto.js');
// proto.js는 브라우저용 ES 모듈(.js)이라 data URL로 읽어 들인다
const src = readFileSync(protoPath, 'utf8');
const P = await import('data:text/javascript;charset=utf-8,' + encodeURIComponent(src));

const lines = readFileSync(process.argv[2], 'utf8').trim().split('\n');
let ok = 0, bad = 0, tiles = 0, setupStates = 0;
for (const line of lines) {
  const d = JSON.parse(line);
  const v = P.parseState(new P.Msg(d.state), d.viewer);
  const r = P.analyze(v);
  for (const [uid, javaCands] of Object.entries(d.cands)) {
    tiles++;
    const js = r.cands.get(Number(uid)) || [];
    if (JSON.stringify(js) !== JSON.stringify(javaCands)) {
      if (bad < 5) console.log('다름', uid, 'java', javaCands, 'js', js);
      bad++;
    } else ok++;
  }
  // 상태 해석 확인: 단계, 시작 패 수, 자리마다 고른 검정 수(비밀이면 -9)
  if (v.phase !== d.phase || v.handSize !== d.hand || JSON.stringify(v.seats.map(s => s.pick)) !== JSON.stringify(d.picks)) {
    bad++; if (bad < 5) console.log('상태 해석 다름', d.phase, v.phase, d.picks, v.seats.map(s => s.pick));
  } else if (d.phase === 'SETUP') setupStates++;
  // 상태 해석 확인: 자리마다 타일 수
  const javaTiles = d.state.split('\t')[9].split('\u001e').map(rec => rec.split('\u001f')[3].split(',').filter(Boolean).length);
  if (JSON.stringify(v.seats.map(s => s.tiles.length)) !== JSON.stringify(javaTiles)) { bad++; if (bad < 5) console.log('타일 수 다름'); }
}
console.log(`ParityTest: 상태 ${lines.length}개(시작 패 고르기 ${setupStates}개 포함), 타일 후보 ${tiles}개 비교 — 같음 ${ok}, 다름 ${bad}`);
process.exit(bad ? 1 : 0);
