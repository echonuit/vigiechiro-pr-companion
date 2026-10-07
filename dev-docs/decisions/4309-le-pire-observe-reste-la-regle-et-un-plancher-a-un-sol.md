---
type: adr
title: "Le pire observé reste la règle, un plancher a un sol, et il ne redescend qu'à la main"
status: stable
article: A5
chantier: "#4309, lot 4 du chantier #5933"
decided_at: 2026-10-07
verification: certaine
enforced_by:
  - ".github/assets/compare_tournages.py"
verification_note: "auto-test joué par lint.yml : cinq cas pour le sol, vus rouges avant lui, et dix-neuf pour le compte de paires sans approche et son signal. Dix mutations tuées, dont trois du sol et sept du compte et du signal ; une a montré une assertion trop lâche, resserrée depuis."
relations:
  amende: ["4287-un-ecart-se-lit-contre-le-plancher-de-son-cas"]
  prolonge: ["5911-un-clip-a-deux-fins-n-a-pas-de-plancher"]
verified:
  - by: machine:ci
    at: 2026-10-07
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-07
---

# Le pire observé reste la règle, un plancher a un sol, et il ne redescend qu'à la main

## Contexte

L'[ADR 4287](4287-un-ecart-se-lit-contre-le-plancher-de-son-cas.md) lit chaque écart contre le
plancher de son cas, et garde pour plancher le **pire** écart observé entre deux tournages du même
commit. Deux reproches lui étaient faits (#4309).

**Un plancher ne redescend jamais.** Un seul mauvais tirage aveuglerait un cas pour de bon, et un
centile, le 80e ou le 90e, garderait la prudence sans cela.

**Un plancher presque nul fait crier du bruit.** Le rapport se calculait contre au moins 0,001 %.
Un écart de 0,015 % s'affichait « ×15 » contre un plancher d'un millième.

Les journaux de trois mesures de planchers portent chaque paire : de 12 à 27 pour 95 clips, lues
le 7 octobre 2026.

| Question | Ce qui est mesuré |
|---|---|
| le fichier permet-il un centile ? | non : il ne garde que le pire et un compte de paires |
| les maximums isolés étaient-ils de mauvais tirages ? | pour les trois plus forts, non : c'étaient des clips à deux fins, à 21 %, 20,9 % et 2,8 % contre un 80e centile sous 0,5 % |
| un plancher reste-t-il trop haut ? | dix dépassent d'au moins moitié le pire de leurs paires récentes, jusqu'à 0,807 % contre 0,263 % |
| combien de planchers sont presque nuls ? | neuf sur cent sous 0,0105 %, dont cinq à zéro |

Ces trois mesures viennent de commits où des clips étaient en cours de correction : quelques cas y
mêlent deux états. Leurs artefacts expirent, et ce relevé ne se rejouera pas.

## Décision

**Le pire observé reste la règle. Un centile est écarté.**

Les maximums les plus isolés étaient des défauts de clip, que
l'[ADR 5911](5911-un-clip-a-deux-fins-n-a-pas-de-plancher.md) a nommés depuis. Un centile les aurait
rangés parmi les cas stables : c'est le pire qui les a montrés. Et les paires d'une mesure ne sont
pas indépendantes. Quatre tournages font six paires, dont un tournage atypique occupe trois : un
80e centile sur les paires n'écarte pas un tournage isolé sur quatre.

**Un plancher a un sol, 0,0105 %.** Aucun rapport ne se calcule contre moins. C'est la moitié de ce
que rend un chiffre changé à l'étalonnage, 0,021 %. Rien de plus petit qu'un chiffre changé ne
dépasse donc ×2, et un chiffre changé se lit ×2. Un plancher mesuré au-dessus du sol n'est pas
touché.

**Un plancher ne redescend qu'à la main, et la mesure dit lequel relire.** Le fichier gagne une
cinquième colonne : le nombre de paires écoulées depuis que le plancher a été approché. Une paire
qui en atteint la moitié remet ce compte à zéro. À dix-huit paires, soit trois mesures de quatre
tournages, la mesure nomme le cas. Elle ne change aucun plancher : le geste reste de retirer la
ligne sur une branche et de remesurer.

## Ce qui a été écarté

**Signaler à chaque mesure le plancher qui dépasse le pire des paires du jour.** C'était le premier
dessin. Rejoué sur les trois journaux, il nommait de 8 à 16 clips par mesure au seuil de ×3, et
jamais les mêmes : le bruit de beaucoup de clips sort par à-coups, et le pire de 48 paires dépasse
naturellement le pire de 6. Quatre clips seulement revenaient à chaque fois. Ce qui distingue un
plancher périmé est la persistance, que le fichier ne portait pas.

**Remesurer dans ce lot les dix planchers trop hauts.** Ils passeraient de 27 à 48 paires à 6. Un
outil plus bavard est l'erreur que #4309 demandait d'éviter.

## Conséquences

- Le sol rend vrai **par construction** qu'aucun écart sous 0,021 % ne dépasse ×2. Un contrôle sur
  deux témoins prouve qu'il est câblé, non qu'il est bien choisi : ce qui le justifie est
  l'étalonnage.
- Sous le sol, un plancher nul et un plancher de 0,010 % se lisent pareil. C'était déjà sous ce
  que la page appelle « rien ».
- Les lignes écrites avant cette décision partent d'un compte nul : on ne sait pas depuis quand
  leur plancher n'a pas été approché. Le signal ne dit rien d'elles avant trois mesures.
- Dix-huit paires et « à moitié près » sont des choix, pas des mesures. Trop bas, le seuil
  retrouve le bavardage écarté ; trop haut, il ne dit jamais rien. Ils se relisent quand le signal
  aura nommé ses premiers cas.
- Un clip à deux fins dont le mode rare ne sort plus sera signalé comme un plancher périmé. Le
  signal ne remplace pas la recherche de la cause.
