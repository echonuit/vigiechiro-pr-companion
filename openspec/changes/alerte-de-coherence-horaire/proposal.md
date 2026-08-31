## Why

L'alerte de cohérence horaire se déclenche quand le protocole est **respecté** et se tait quand il
est **violé**. Un observateur qui commence 30 minutes avant le coucher du soleil, ce que le protocole
Vigie-Chiro exige, reçoit un avertissement ; celui qui commence 30 minutes après, ce qui viole le
protocole, n'en reçoit aucun.

Le défaut est une prémisse écrite dans le modèle : la javadoc de `CoherenceHoraire` énonce que « le
matériel devrait démarrer après le coucher et s'arrêter avant le lever », l'exact contraire de ce que
le brief demande depuis toujours. Le code applique la javadoc.

Signalé par un observateur de terrain le 2026-08-29, sur la version 2.189.0, après une campagne en
Corse.

## What Changes

- Le protocole devient un **plancher** et non une cible : commencer plus tôt ou finir plus tard
  couvre la fenêtre exigée et davantage, ce qui n'est pas un écart.
- L'alerte binaire « hors nuit » est remplacée par **trois niveaux**, dont un seul est un défaut de
  protocole et un autre une interruption du matériel.
- **Aucun seuil en minutes.** Les bandes 5 / 30 minutes envisagées par le story mapping mesuraient un
  écart à une cible ; il n'y a pas de cible, il y a une fenêtre à couvrir.
- La plage **théorique** et la plage **effective** deviennent visibles, là où l'écran ne montrait
  qu'un booléen.
- **BREAKING** pour les lecteurs de `CoherenceHoraire` : les deux booléens `demarrageHorsNuit` et
  `arretHorsNuit` disparaissent au profit du niveau et des deux plages. Les deux surfaces qui les
  lisent, l'écran Diagnostic et la commande `diagnostiquer`, changent ensemble.

## Capabilities

### New Capabilities

- `diagnostic/coherence-horaire-d-une-nuit` : dire à l'observateur si sa nuit couvre la fenêtre que
  le protocole exige, de combien elle la dépasse ou lui manque, et si le capteur a consigné une
  interruption en cours de route.

### Modified Capabilities

Aucune. Les deux capacités déclarées du dépôt, `sites/declaration-de-carre` et
`passage/emport-d-une-nuit`, ne changent pas d'exigence.

## Impact

**Code** : `AnalyseCoherenceHoraire` et `CoherenceHoraire` dans `diagnostic/model` ; leur restitution
par `DiagnosticController` et `DiagnosticViewModel` ; la commande `diagnostiquer` de la CLI.
`CycleAcquisition`, déjà porteur de la complétude d'une nuit, alimente le troisième niveau sans
changer.

**Documentation** : `docs/ecrans/diagnostic.md`, la maquette `M-Diagnostic.md` et le story mapping
`E6`, qui écrivent aujourd'hui la règle inversée ou un manque désormais comblé.

**Hors périmètre** : la couverture horaire déduite des horodatages WAV, indicateur 1 de `P3`. Une
nuit calme et une nuit interrompue s'y ressemblent, et le dépôt n'a pas la population de journaux qui
permettrait de fixer le seuil qui les sépare. Ce qu'on ne peut pas savoir ne se décide pas.
