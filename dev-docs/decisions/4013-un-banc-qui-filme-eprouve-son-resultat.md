# ADR 4013 - Un banc qui filme éprouve ses gestes ET son résultat

- **Statut** : Accepté - 2026-08-20
- **Chantier** : #4013, suite de #3887
- **Vérification** : certaine - `scripts/doc-video/filme-un-parcours.sh`

Le cas qui tient cette décision : « une exigence non satisfaite refuse », dans l'auto-test du banc.

## Contexte

Le banc de documentation vérifie chaque geste avant de l'exécuter : `viser` lit le libellé à l'endroit
visé, et refuse si ce n'est pas lui. C'était censé garantir qu'un film montre le parcours annoncé.

⚠️ **Ce n'était pas suffisant, et le banc a rendu ✅ sur un film où rien n'arrive.** Un `viser` réussi
prouve qu'un bouton était là et qu'on a cliqué dessus. Il ne prouve **jamais** que le clic a fait
quelque chose.

Le seul geste du parcours qui ne passait pas par un libellé - le clic sur l'item d'une liste
déroulante, dont la position dépend de la hauteur de tout ce qui la précède - est tombé à côté après
qu'une feuille de style du socle (#4023) eut déplacé la mise en page de quatre pixels. Le film
montrait trente-quatre secondes d'un formulaire jamais rempli, bouton d'import grisé jusqu'au bout.
Fichier valide, montage propre, index juste.

Un banc qui éprouve les gestes et pas les résultats produit exactement le genre de faux qu'il existe
pour empêcher.

## Décision

**1. Chaque parcours EXIGE ce qu'il a promis de montrer.** `exiger_a_l_ecran` lit un libellé sans
rien cliquer, et le tournage échoue s'il est absent. Un parcours d'importation exige « Import
terminé » ; celui du journal illisible exige le **motif du refus**, puisqu'il ne se termine pas par un
import.

**2. Une exigence par parcours, à la fin, et non une par geste.** Contrôler aussi le choix du point
d'écoute en cours de route a coûté trois réglages de coordonnées - la cible se déplace avec le
défilement, et la valeur d'une liste (« A1 ») fait deux caractères dont l'OCR ne tire rien. Or
l'exigence finale la **subsume** : sans point rattaché, l'import ne part pas. Un contrôle qui en
implique un autre rend l'autre inutile, et deux contrôles fragiles valent moins qu'un seul qui tienne.

**3. L'exigence se pose là où l'écran ne défile pas** - la barre d'état. Exiger un texte dans le corps
de la page dépendrait de la position du défilement, donc du contenu : un contrôle qui varierait avec
ce qu'il contrôle.

**4. Aucun geste ne dépend d'un pixel s'il peut l'éviter.** Le clic d'item passe au clavier (`Down`,
`Return`), qui prend le premier élément quelle que soit sa position - et qu'un utilisateur au clavier
ferait de toute façon.

**5. Le banc tolère un reflux de mise en page, et il le DIT.** `viser` balaie verticalement par pas de
quatre pixels et annonce l'écart retenu. Refuser à chaque changement de deux lignes de CSS ne sert
personne ; l'absorber en silence laisse les scénarios pourrir sans que personne ne le sache.

⚠️ Le pas est de **quatre** pixels, pas huit : mesuré, un bouton se trouvait à dix pixels du point
visé ; à l'écart +8 la fenêtre de lecture le rognait et rendait « uler | | », à +10 elle rendait
« + Ajouter ». Deux pixels séparaient le refus de la lecture juste.

## Conséquences

Un dispositif qui vérifie des **actions** doit vérifier au moins un **effet**. La question à poser à
tout harnais de ce genre : « ce vert existerait-il si l'application ne faisait rien ? »

`lire_zone` refuse désormais une image absente. Une chaîne vide était indiscernable de « rien d'écrit
à cet endroit », et six mesures ont été calibrées sur un fichier qui n'existait pas avant qu'on s'en
aperçoive.

## Ce qui a été écarté

**Viser par le graphe de scène** plutôt que par l'image. JavaFX 26 n'implémente l'accessibilité ni
sous GTK ni via `javax.accessibility` : aucun fichier d'accessibilité dans `native-glass/gtk`, zéro
usage de `javax/accessibility` dans les jars, et côté OpenJFX aucune issue Linux depuis 2014. Le
libellé se lit donc à l'écran, par OCR, faute d'un autre chemin.
