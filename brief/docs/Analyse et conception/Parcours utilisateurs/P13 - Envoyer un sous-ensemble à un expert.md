# P13 - Envoyer un sous-ensemble à un expert 📤

[← Retour au sommaire des parcours](index.md) · **Section C - Après le dépôt & exploitation**

> **Persona principal** : Samuel. **Objectifs qualité visés** : fiabilité des données (une
> identification douteuse est tranchée par quelqu'un qui peut réécouter).

Samuel relit sa saison et tombe sur du **grand Rhinolophe en pleine ville** - plausible mais
étonnant. Il connaît une spécialiste du genre et veut lui envoyer **ces observations-là, avec leurs
sons** : un tableau ne lui suffira pas, elle voudra écouter. Aujourd'hui il lui faudrait retrouver
les fichiers un à un dans les dossiers de session.

1. Depuis « Espèces & observations » (parcours [P11](P11%20-%20Inventaire%20des%20espèces%20détectées.md)),
   Samuel clique sur l'espèce : la vue audio s'ouvre sur **tous ses grands Rhinolophes**.
2. Il ajoute la puce « **Lieu** » et coche la commune qui l'intrigue : le sous-ensemble affiché est
   exactement ce qu'il veut soumettre (les filtres se combinent - espèce × lieu, et au besoin la
   probabilité ou la plage horaire).
3. « **☰ → Exporter les observations et les sons (ZIP)…** » : il choisit la destination, une fenêtre
   de progression annonce le contenu (« 412 observations · 405 sons · ~567 Mo ») puis avance fichier
   par fichier, annulable à tout instant.
4. L'archive contient `observations.csv` (le CSV habituel : ses corrections, sa certitude, la
   commune) et les sons rangés **par nuit** (`sons/<session>/…`). Un son dont le fichier a quitté le
   disque est **compté** dans le compte rendu, jamais bloquant.
5. Samuel envoie l'archive par le moyen de son choix ; l'experte écoute avec n'importe quel lecteur
   audio et retrouve chaque son depuis le CSV.

En **ligne de commande**, `vigiechiro exporter-sons --espece Rhifer --sortie rhinolophes.zip` (ou
`--passage <id>`) produit la même archive - pour scripter des envois réguliers.

## Variante

L'envoi lui-même (messagerie, dépôt de fichiers, lien de partage) reste **hors de l'application** :
l'archive est le produit fini. Un envoi intégré supposerait un service tiers et des identifiants de
plus, pour un geste que chacun a déjà ailleurs.
