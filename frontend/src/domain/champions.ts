import leeSinPortrait from '../assets/champions/LeeSin.png';
import viegoPortrait from '../assets/champions/Viego.png';
import ahriPortrait from '../assets/champions/Ahri.png';
import oriannaPortrait from '../assets/champions/Orianna.png';
import threshPortrait from '../assets/champions/Thresh.png';
import luluPortrait from '../assets/champions/Lulu.png';
import jinxPortrait from '../assets/champions/Jinx.png';
import kaisaPortrait from '../assets/champions/Kaisa.png';
import syndraPortrait from '../assets/champions/Syndra.png';
import azirPortrait from '../assets/champions/Azir.png';
import { CHAMPION_CATALOG } from './championCatalog';

// Keep data and images on the same verified Data Dragon version.
const DATA_DRAGON_VERSION = '16.18.1';
const LOCAL_PORTRAITS: Readonly<Record<string, string>> = {
  LeeSin: leeSinPortrait,
  Viego: viegoPortrait,
  Ahri: ahriPortrait,
  Orianna: oriannaPortrait,
  Thresh: threshPortrait,
  Lulu: luluPortrait,
  Jinx: jinxPortrait,
  Kaisa: kaisaPortrait,
  Syndra: syndraPortrait,
  Azir: azirPortrait,
};

function normalizedName(name: string): string {
  return name.normalize('NFKC').toLowerCase().replace(/[\s.'’·_-]/g, '');
}

const championsByName = new Map(
  CHAMPION_CATALOG.flatMap(([id, koreanName, englishName, filename]) => {
    const champion = { name: koreanName, portrait: LOCAL_PORTRAITS[id]
      ?? `https://ddragon.leagueoflegends.com/cdn/${DATA_DRAGON_VERSION}/img/champion/${filename}` };
    return [id, koreanName, englishName].map(name => [normalizedName(name), champion] as const);
  }),
);

export function championPortrait(name: string): string | null {
  return championsByName.get(normalizedName(name))?.portrait ?? null;
}

export function championName(name: string): string | null {
  return championsByName.get(normalizedName(name))?.name ?? null;
}
