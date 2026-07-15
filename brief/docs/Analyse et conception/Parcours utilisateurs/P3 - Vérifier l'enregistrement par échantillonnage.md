# P3 - Vérifier l'enregistrement par échantillonnage 🎧

[← Retour au sommaire des parcours](index.md) · **Section B - Chaîne de production**

> **Persona principal** : Marie / Karim / Samuel. **Objectifs qualité visés** : [O4 Exactitude lecture audio](../../Objectifs%20qualités/Objectifs%20qualités/O4.md), [O7 Intégrité](../../Objectifs%20qualités/Objectifs%20qualités/O7.md).

Marie vient d'importer une nuit (parcours [P2](P2%20-%20Importer%20une%20nuit%20d%27enregistrement.md)). Avant de la déposer sur Vigie-Chiro, elle veut **s'assurer que la nuit s'est bien passée et que la qualité audio est exploitable**. La vérification se fait en **deux temps complémentaires** :

1. **Pré-check synthétique** (rapide, sans écoute) : la nuit a-t-elle produit assez de fichiers, sur la bonne plage horaire, avec un renommage cohérent ? C'est la vérification que pratique [Samuel](../Personas/Samuel.md) par défaut : la grande majorité des fichiers étant du bruit, il préfère envoyer directement à Tadarida sans écouter au préalable, et concentre son écoute sur les fichiers d'intérêt remontés par le tableur de résultats.
2. **Sound check par échantillonnage** (audio, plus long) : écouter quelques séquences réparties sur la nuit pour détecter un défaut acoustique global (saturation, micro HS, parasite continu). [Marie](../Personas/Marie.md) utilise systématiquement ce niveau pour se rassurer ; [Karim](../Personas/Karim.md) et Samuel l'enclenchent surtout en cas de doute remonté par le pré-check.

C'est un **sound check global**, distinct de la validation taxonomique espèce par espèce qui viendra plus tard (parcours [P7](P7%20-%20Valider%20les%20résultats%20Tadarida.md)).

## Étape 1 - Pré-check synthétique de la nuit

Marie ouvre la vue détail du passage qui vient d'être importé. Un encart **« État de la nuit »** affiche trois indicateurs sous forme de feux (🟢 OK / 🟠 suspect / 🔴 anomalie) :

1. **Couverture horaire** : la plage `premier WAV → dernier WAV` couvre-t-elle bien la plage théorique `coucher de soleil - 30 min → lever de soleil + 30 min` ([R3](../Modèle%20conceptuel/Règles%20métier.md#r3)) ? La plage astronomique est calculée à partir des coordonnées GPS du point (si renseignées en [C3](../Modèle%20conceptuel/C3%20-%20Point%20d%27écoute.md)) et de la date. Une tolérance est appliquée car le PR peut démarrer en retard si le site est bruyant ou se mettre en veille avant la fin si la batterie faiblit - l'indicateur passe à 🟠 quand l'écart dépasse 30 min d'un côté, à 🔴 si une moitié de nuit complète manque.
2. **Nombre de fichiers** : le nombre d'enregistrements originaux est-il dans la fourchette attendue (typiquement quelques centaines à quelques milliers) ? Indicateur 🟠 si < 50 (nuit anormalement creuse, à recouper avec la météo du site), 🔴 si 0.
3. **Cohérence du renommage** : tous les WAV portent-ils bien le préfixe `CarXXXXXX-AAAA-PassN-YY-` attendu ([R6](../Modèle%20conceptuel/Règles%20métier.md#r6)), avec le bon numéro de carré, la bonne année, le bon n° de passage et le bon point ? Indicateur 🔴 dès qu'un fichier diverge.

Pour creuser les détails (courbes T°/H, événements anormaux du journal du capteur, comparaison batterie inter-passages), Marie peut basculer vers la vue diagnostic du matériel ([P6 - Diagnostiquer le matériel](P6%20-%20Diagnostiquer%20le%20matériel.md)).

## Étape 2 - Sound check par échantillonnage (optionnel selon le profil)

Si Marie souhaite confirmer auditivement l'absence de problème acoustique (saturation, micro HS, parasite continu), elle ouvre l'onglet **« Écouter la sélection »** :

1. L'application constitue automatiquement une **sélection d'écoute** : 10 à 30 séquences d'écoute réparties uniformément sur la nuit (méthode `RéparTemporel` par défaut, [R12](../Modèle%20conceptuel/Règles%20métier.md#r12)).
2. La sélection s'affiche sous forme de liste chronologique. Pour chaque séquence : horodatage, durée, fréquence dominante (indicative), bouton ▶ pour écouter, et un **verdict par fichier** à poser après écoute (`Bon` / `Mauvais` / `Inexploitable`).
3. Marie écoute quelques séquences à des moments différents de la nuit et **juge chacune**. Comme les séquences sont **déjà ralenties ×10 sur disque** ([R10](../Modèle%20conceptuel/Règles%20métier.md#r10)), la lecture se fait à vitesse normale, sans transformation à la volée - l'audio est immédiatement audible pour l'oreille humaine. Une **barre de progression tricolore** (vert / orange / rouge) résume au fil de l'eau les verdicts posés.
4. Marie peut compléter sa sélection si elle en ressent le besoin (changer la méthode pour `Aléatoire`, augmenter la taille à 50 séquences, ou ajouter manuellement une séquence à un moment précis).

> 💡 **Quand utiliser le sound check ?** Marie, débutante, s'en sert systématiquement par sécurité. Samuel, par expérience, sait que la majorité des fichiers sont du bruit et préfère envoyer directement à Tadarida sans écouter - il attend le tableur de résultats pour cibler son écoute sur les fichiers d'intérêt. Karim navigue entre les deux selon le chantier.

## Étape 3 - Verdict final du passage

Une fois l'échantillon écouté (pré-check seul ou pré-check + sound check), l'application **propose un verdict final du passage dérivé** des verdicts par fichier : `OK` (tout est bon), `Utilisable` (quelques défauts mais exploitable) ou `Inexploitable`. L'utilisateur garde le **dernier mot** : il peut **forcer** ce verdict, et le compléter par un commentaire libre (« vent fort vers 02:00, sons à vérifier »).

Le passage passe au statut `Vérifié` et le verdict est mémorisé. Un passage `Inexploitable` doit être **requalifié** (re-vérifié) avant de pouvoir être déposé ([R14](../Modèle%20conceptuel/Règles%20métier.md#r14)). Marie peut enchaîner sur la préparation du dépôt ([P4](P4%20-%20Préparer%20un%20lot%20prêt%20à%20déposer.md)).

## Règles métier visibles

- [R3](../Modèle%20conceptuel/Règles%20métier.md#r3) : plage théorique d'enregistrement (coucher -30 min → lever +30 min), source du check de couverture horaire.
- [R6](../Modèle%20conceptuel/Règles%20métier.md#r6) : préfixe `CarXXXXXX-AAAA-PassN-YY-` (tirets du 6), source du check de cohérence du renommage.
- [R10](../Modèle%20conceptuel/Règles%20métier.md#r10) : séquences d'écoute ralenties ×10 sur disque, lecture sans transformation à la volée.
- [R12](../Modèle%20conceptuel/Règles%20métier.md#r12) : sélection d'écoute constituée automatiquement (méthode `RéparTemporel` par défaut).
- [R13](../Modèle%20conceptuel/Règles%20métier.md#r13) : verdict par fichier son, verdict final dérivé et surchargeable ; l'utilisateur reste responsable - aucun seuil minimum d'écoute imposé.
- [R14](../Modèle%20conceptuel/Règles%20métier.md#r14) : un passage `Inexploitable` ne peut pas être déposé (alerte bloquante au moment du parcours [P4](P4%20-%20Préparer%20un%20lot%20prêt%20à%20déposer.md)).
