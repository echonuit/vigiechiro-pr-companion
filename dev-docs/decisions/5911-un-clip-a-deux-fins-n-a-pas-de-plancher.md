---
type: adr
title: "Un clip à deux fins n'a pas de plancher, et la comparaison le dit"
status: stable
article: A11
chantier: "#5911, trouvé par le lot 10 du sous-chantier #5644"
decided_at: 2026-10-05
verification: certaine
enforced_by:
  - ".github/assets/compare_tournages.py"
verification_note: "l auto-test tient qu un clip nommé est mesuré et affiché sans être écrit, qu il n entre pas dans le plancher le plus haut, que sa ligne quitte le fichier et que la comparaison l annonce avec son issue, quatre mutants tués. Rien ne tient le choix des clips nommés : c est la dernière image, regardée, et le contrôle ne sait pas regarder"
relations:
  complete: ["5885-un-plancher-appartient-a-l-instrument-qui-l-a-pris", "4287-un-ecart-se-lit-contre-le-plancher-de-son-cas"]
verified:
  - by: machine:ci
    at: 2026-10-05
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-05
---

# Un clip à deux fins n'a pas de plancher, et la comparaison le dit

## Contexte

Un plancher mesure le bruit d'un clip qui finit sur son verdict : deux tournages du même commit
montrent le même écran, et ne diffèrent que par le rendu.

Sept clips ne sont pas dans ce cas. Mesurés sur six tournages du même commit, ils ont **deux fins** :
leurs paires valent soit moins de 3 %, soit de 9 à 26 %, jamais entre les deux. Leurs dernières images
le montrent : un import encore en cours, une page prise pendant son fondu, une modale défilée à trois
endroits. Pour quatre d'entre eux la seconde fin ne sort qu'un tournage sur quatre, et la paire unique
qui avait précédé cette mesure ne l'avait pas vue.

La règle du pire observé leur donnerait 26 %, 21 % ou 9,7 % de plancher. La comparaison serait alors
aveugle, sur ces clips, à tout changement plus petit : un encart entier changé vaut 4,2 %.

## Décision

**Un clip à deux fins est nommé dans l'outil, avec son issue, et ne reçoit pas de plancher.**

La table `SANS_PLANCHER` de `compare_tournages.py` porte ces clips. Pour chacun :

- la mesure calcule son écart et l'**affiche**, sans l'écrire ni le compter parmi les planchers ;
- une ligne que le fichier porterait déjà en est retirée ;
- la comparaison l'annonce « sans plancher », suivi du numéro, et non « plancher inconnu ».

Les deux silences ne se réparent pas au même endroit. Un plancher inconnu se mesure. Un clip sans
plancher se corrige, dans son scénario.

**Le critère est la dernière image, regardée.** Un clip entre dans la table quand deux tournages du
même commit ne montrent pas le même écran, pas quand son chiffre est haut. Il en sort avec l'issue
qui le porte, et reçoit alors son plancher par l'atelier.

## Ce qui a été écarté

**Un seuil.** Au-delà de tant de pour cent, pas de plancher. Mais un clip bruyant et un clip à deux
fins ne se distinguent pas par un chiffre : le second a des paires basses et des paires hautes, le
premier a des paires étalées. Et un seuil absolu se périme en silence.

**Leur écrire le plancher mesuré.** C'est la règle générale, et elle rend la comparaison muette là où
un défaut est connu.

**Retirer leurs lignes à la main.** L'atelier les récrirait à la mesure suivante.

## Conséquences

La comparaison ne dit rien d'un changement sur ces sept clips tant qu'ils ne sont pas corrigés. Elle
le dit, à chaque fois, avec le numéro à suivre : #5893 pour trois d'entre eux, #5911 pour les quatre
autres.

**Mise à jour du 5 octobre 2026.** Le compte de sept est celui du jour de cette décision, et il
baisse à mesure que les clips reçoivent leur remède. Le premier est sorti de la table : le clip du
menu de ligne de l'import finit désormais sur son compte rendu, calé au bas de sa page, et a reçu son
plancher (#5952). Six restent, et tout ce que cette page décide vaut pour eux sans changement.

**Mise à jour du 6 octobre 2026.** Les trois clips de l'écran d'import sont sortis à leur tour
(#5893). Deux finissaient sitôt leur compte rendu visible, sans caler la page ni tenir l'image ; le
troisième attendait un libellé qui n'est jamais vide, et finissait pendant la décompression. Restent
ceux de #5911.

Une table de noms dans un outil vieillit si personne ne la relit. Chaque entrée porte donc son issue,
dont le critère de fin comprend la sortie de la table.

Quatre tournages voient une seconde fin qui sort une fois sur quatre, pas une fois sur vingt. D'autres
clips peuvent en avoir une plus rare.
