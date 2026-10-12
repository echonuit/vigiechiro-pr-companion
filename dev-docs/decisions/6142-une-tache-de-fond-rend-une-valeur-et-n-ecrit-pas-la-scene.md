---
type: adr
title: "Une tâche de fond rend une valeur et n'écrit pas la scène, et un espion de test le relève"
status: stable
article: A2
chantier: "#5697 (les bascules des bancs), lot #6142"
decided_at: 2026-10-07
verification: probable
enforced_by:
  - "scripts/adr/6142-banc-a-toolkit-sans-espion.py"
ratchet: 0
verified:
  - by: machine:suspects
    at: 2026-10-07
relations:
  complete: ["5707-un-geste-du-pointeur-situe-sa-cible-sur-le-fil"]
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-07
---

# Une tâche de fond rend une valeur et n'écrit pas la scène, et un espion de test le relève

## Contexte

À chaque source désignée sur l'écran d'import, la tâche de fond écrivait une propriété dont dépend
le texte de `#champDossier` (#6138). Tous les bancs de l'import la jouaient, et aucun ne l'a vue :
il a fallu qu'une pile de la forge la nomme, sur une demande sans rapport.

JavaFX ne protège pas de ce défaut. Lu par `javap` dans les jars de la version 26 :
`Toolkit.checkFxUserThread()` garde la liste des enfants d'un nœud montré, une prise de vue,
l'ouverture d'une fenêtre, et aucune écriture de propriété. Aucune propriété système n'étend cette
vérification.

La règle n'était écrite qu'en prose, dans `patterns.md`, `performance.md` et la javadoc
d'`ExecuteurTache`.

## Décision

**Une tâche d'arrière-plan ne touche aucune propriété dont dépend un nœud montré.** Elle rend une
valeur, et c'est le retour de succès, joué sur le fil JavaFX, qui l'applique.

**Un espion de test le relève.** `EcrituresHorsDuFil` pose par ByteBuddy une sonde à l'entrée des
méthodes par lesquelles une propriété JavaFX annonce un changement : `markInvalid` des
`*PropertyBase` et de `TextInputControl$TextProperty`, `fireValueChangedEvent` des
`ReadOnly*PropertyBase`. Une écriture directe, une liaison et un écouteur y passent tous, sur le fil
de l'écrivain. Elle retient l'écriture quand le porteur de la propriété est un nœud attaché à une
scène dont la fenêtre est montrée, et que le fil n'est pas celui de JavaFX.

L'extension `SansEcritureHorsDuFil` ouvre ce relevé au début du cas et fait rougir le cas à sa fin,
en nommant le nœud, ses propriétés, le fil et la chaîne des lignes du produit. Elle relève puis
affirme, parce que JavaFX avale ce qu'un écouteur lève.

**Toute classe de test qui monte le toolkit la porte**, bancs filmés compris. Le garde
`scripts/adr/6142-banc-a-toolkit-sans-espion.py` lit les `@ExtendWith` par l'arbre syntaxique et
refuse, sans marge, celui qui nomme `ApplicationExtension` sans elle. Une seule classe en est
dispensée, l'auto-test de l'extension, qui pilote le relevé lui-même.

**Elle s'éteint d'elle-même pendant une séance filmée.** Elle demande d'abord `Seance.filmee()`,
qui reconnaît les deux chaînes de film, et ne charge alors ni son registre ni ByteBuddy : rien ne
s'ajoute à ce qu'un clip mesure. Elle n'a pas de clé à elle, qu'il faudrait tenir d'accord avec
celles des tournages. `SansEcritureHorsDuFilEteintTest` le prouve dans une JVM à part, pour chacun
des deux signaux.

## Ce qui a été mesuré

Le défaut de #6138 remis à la main fait rougir les trois cas de `ScenarioCarteMultiNuitsTest`, verts
jusque-là. Sur `0631c4068b`, la suite entière avec l'espion actif : 5 919 cas, 46 860 invalidations
hors du fil, toutes venues du fil du banc, et **aucune écriture d'un nœud montré par le produit**.
Deux tirs de la suite, avec et sans, ne montrent pas de coût, sans dispersion connue.

## Ce que l'espion ne voit pas

Sa vérification est « probable » : le garde tient la population des bancs, pas la règle elle-même.

- Il ne voit que ce que les bancs jouent. Les treize classes connectées, exclues d'une demande par
  leur étiquette, ne sont pas regardées (#6136).
- Il ne voit pas une lecture hors du fil.
- Il ne voit pas une liste observable modifiée sans qu'aucune propriété ne bouge ensuite, ni les
  propriétés calculées qui n'héritent pas d'une base, comme les bornes d'un nœud.
- Il ne voit pas une tâche qui écrit après la fin de son cas.
- Un toolkit démarré sans `ApplicationExtension` sort de la population du garde.

**Il laisse passer exprès** un nœud pas encore attaché à une scène montrée, qu'on a le droit de
bâtir hors du fil, et une propriété de modèle de vue dont rien de la scène ne dépend.

**Une écriture faite par le fil du banc est consignée et ne fait pas rougir.** C'est un défaut du
banc et non du produit, d'une autre famille que les gestes du pointeur de
l'[ADR 5707](5707-un-geste-du-pointeur-situe-sa-cible-sur-le-fil.md). La faire rougir est une
décision qui n'est pas prise.

## Ce qui reste fragile

`markInvalid` est une méthode privée de JavaFX. Une montée de version qui la renomme rendrait
l'espion muet. `SansEcritureHorsDuFilTest` le verrait : il exige qu'une écriture hors du fil soit
relevée, et qu'une écriture sur le fil soit comptée.

## Alternatives écartées

- **Un balayeur statique des tâches.** Il ne suit pas une écriture à travers un écouteur, et c'est
  par un écouteur que celle de #6138 atteignait le champ.
- **Écouter des propriétés choisies, ou la mise en page de la racine.** La liste a des trous qu'on
  ne connaît pas, et la mise en page est muette quand elle est déjà demandée.
- **L'inscription au fichier de services de JUnit.** L'espion aurait été muet en CI et actif sur les
  tournages.
