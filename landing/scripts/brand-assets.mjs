import {readFile, writeFile, mkdir} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import path from 'node:path';
import sharp from 'sharp';

// Use the app's canonical vector; all landing variants are generated output.
export const brandSource = new URL('../../frontend/public/brand/queuemate-mark.svg', import.meta.url);

export async function buildBrandAssets(out) {
  const mark = await readFile(brandSource, 'utf8');
  const favicon = mark.replace('viewBox="0 0 622 668"', 'viewBox="-69 -46 760 760"');
  if (favicon === mark) throw new Error('Unexpected brand viewBox');
  await mkdir(path.join(out, 'assets'), {recursive: true});
  await writeFile(path.join(out, 'assets/queuemate-mark.svg'), mark);
  await writeFile(path.join(out, 'favicon.svg'), favicon);
  const sizes = [16, 32, 48];
  const pngs = await Promise.all(sizes.map(size => sharp(Buffer.from(favicon)).resize(size, size).png().toBuffer()));
  const directory = Buffer.alloc(6 + 16 * pngs.length);
  directory.writeUInt16LE(1, 2);
  directory.writeUInt16LE(pngs.length, 4);
  let offset = directory.length;
  pngs.forEach((png, index) => {
    const entry = 6 + index * 16;
    directory[entry] = directory[entry + 1] = sizes[index];
    directory.writeUInt16LE(1, entry + 4);
    directory.writeUInt16LE(32, entry + 6);
    directory.writeUInt32LE(png.length, entry + 8);
    directory.writeUInt32LE(offset, entry + 12);
    offset += png.length;
  });
  await writeFile(path.join(out, 'favicon.ico'), Buffer.concat([directory, ...pngs]));
  await sharp(Buffer.from(favicon)).resize(180, 180).flatten({background: '#ffffff'}).png().toFile(path.join(out, 'apple-touch-icon.png'));
  return createHash('sha256').update(mark).digest('hex').slice(0, 12);
}
