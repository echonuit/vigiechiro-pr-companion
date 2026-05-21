# E6 - 🩺 Diagnostiquer le matériel

[← Retour au hub story mapping](index.md) · **Parcours principal** : [P6 - Diagnostiquer le matériel](../Parcours%20utilisateurs/P6%20-%20Diagnostiquer%20le%20matériel.md) · 🟠 SHOULD

**Portée** : exploiter le **journal du capteur** (`LogPR<n>.txt`) et le **relevé climatique** (`PaRecPR<sn>_THLog.csv`) capturés pendant la nuit pour donner à l'utilisateur une vue d'ensemble de la santé matérielle de son enregistreur. Comprend également la **vérification astronomique** (idée Samuel, mai 2026) qui compare la plage effective d'enregistrement à la plage théorique calculée d'après les coordonnées GPS du point.

**Persona principal** : Karim et Samuel (exploitation pro avec plusieurs enregistreurs à surveiller). Marie en a peu besoin sur son unique enregistreur.

**Pré-requis** : [E2.S1](E2%20-%20Importer%20et%20transformer%20une%20nuit.md#e2s1) (journal et climat copiés depuis la SD lors de l'import), [E1.S3](E1%20-%20Gérer%20ses%20sites%20et%20points%20de%20suivi.md#e1s3) pour les coordonnées GPS (S3 uniquement).

## E6.S1 - Visualiser les graphes de température et d'hygrométrie sur la nuit { #e6s1 }

**En tant que** [Karim](../Personas/Karim.md)

**Je veux** voir les courbes de température et d'hygrométrie de la nuit sur un même panneau

**Afin de** détecter une dérive ou une anomalie de la sonde climatique (gel inattendu, condensation excessive, etc.)

**Critères d'acceptation** :

- [ ] L'onglet « Diagnostic » de la fiche détail d'un passage affiche deux courbes superposées (ou empilées) : température (axe gauche, °C) et hygrométrie (axe droit, %).
- [ ] L'axe X est temporel et couvre toute la plage de la nuit (du premier au dernier enregistrement).
- [ ] Les données sont parsées depuis le fichier `PaRecPR<sn>_THLog.csv` (une mesure toutes les 600 s, colonnes `Date;Hour;Temperature;Humidity`).
- [ ] Si le relevé climatique est **absent** (sonde défaillante ou non installée), un message explicite remplace les graphes : « Pas de relevé climatique disponible pour ce passage. Sonde absente ou défaillante ? » ([R20](../Modèle%20conceptuel/Règles%20métier.md#r20)).
- [ ] Survol d'un point : tooltip avec timestamp précis + valeur exacte.
- [ ] Les graphes se redessinent proprement même si le passage couvre plusieurs heures (1000+ points possibles).

**Parcours rattaché** : [P6](../Parcours%20utilisateurs/P6%20-%20Diagnostiquer%20le%20matériel.md), étape 2 (sous-bloc T°/H)<br>
**Maquettes cibles** : [M-Passage](../Maquettes/M-Passage.md) (onglet Diagnostic, partie haute)<br>
**Dépendances** : [E2.S1](E2%20-%20Importer%20et%20transformer%20une%20nuit.md#e2s1), [E2.S4](E2%20-%20Importer%20et%20transformer%20une%20nuit.md#e2s4)<br>
**Complexité** : ★★★ (moyen — parse CSV + JavaFX LineChart à 2 axes + gestion du cas absent)<br>
**MoSCoW** : 🟠 SHOULD

---

## E6.S2 - Visualiser le niveau de batterie et lister les évènements anormaux { #e6s2 }

**En tant que** [Karim](../Personas/Karim.md)

**Je veux** voir le niveau de batterie du PR au début et à la fin de la nuit, ainsi que la liste des évènements techniques anormaux qui se sont produits (réveils non programmés, erreurs SD, redémarrages, alerte batterie critique)

**Afin de** identifier rapidement les passages où le matériel a eu un comportement inattendu et décider s'il faut intervenir

**Critères d'acceptation** :

- [ ] L'onglet « Diagnostic » affiche un encart « Batterie » avec : tension au démarrage, tension à la mise en veille, écart (delta).
- [ ] Sous l'encart batterie, une **liste chronologique** des évènements anormaux extraits du `LogPR<n>.txt` :
    - réveils non programmés (« wakeup non attendu »)
    - erreurs SD (« SD card error »)
    - redémarrages inopinés
    - alertes batterie critique
- [ ] Chaque entrée affiche : horodatage, type d'évènement, message complet du journal.
- [ ] Les évènements sont classés par gravité (icône ⚠ pour anomalies mineures, ❌ pour critiques).
- [ ] Si le journal du capteur est **partiel** (cas du log circulaire saturé, [R19](../Modèle%20conceptuel/Règles%20métier.md#r19)), une note explicite signale : « Le journal du capteur a été partiellement effacé en cours de nuit (SD saturée). Certaines anomalies antérieures à HH:MM peuvent manquer. »
- [ ] Si le journal est **totalement absent**, l'encart est masqué avec un message explicite (mais le parcours ne plante pas, cf. [E2.S1](E2%20-%20Importer%20et%20transformer%20une%20nuit.md#e2s1)).

**Parcours rattaché** : [P6](../Parcours%20utilisateurs/P6%20-%20Diagnostiquer%20le%20matériel.md), étape 2 (sous-blocs batterie + évènements)<br>
**Maquettes cibles** : [M-Passage](../Maquettes/M-Passage.md) (onglet Diagnostic, encart batterie + liste évènements)<br>
**Dépendances** : [E2.S1](E2%20-%20Importer%20et%20transformer%20une%20nuit.md#e2s1), [E2.S4](E2%20-%20Importer%20et%20transformer%20une%20nuit.md#e2s4)<br>
**Complexité** : ★★★ (moyen — parser du format texte LogPR + extraction patterns de batterie + classification des évènements)<br>
**MoSCoW** : 🟠 SHOULD

---

## E6.S3 - Vérifier la cohérence des horaires d'enregistrement vs astronomie locale { #e6s3 }

**En tant que** [Samuel](../Personas/Samuel.md)

**Je veux** que l'application calcule les heures réelles de coucher/lever de soleil pour la date et le lieu d'enregistrement, et compare ces horaires avec la plage effective enregistrée par le PR

**Afin de** détecter un PR mal programmé qui a enregistré trop tôt, trop tard, ou pas du tout selon le protocole Vigie-Chiro (allumage 30 min avant coucher → 30 min après lever)

**Critères d'acceptation** :

- [ ] L'onglet « Diagnostic » affiche un encart « Cohérence horaires » uniquement si les coordonnées GPS du point sont saisies (cf. [E1.S3](E1%20-%20Gérer%20ses%20sites%20et%20points%20de%20suivi.md#e1s3)).
- [ ] L'encart affiche :
    - heure de coucher du soleil **calculée localement** d'après les coordonnées GPS et la date d'enregistrement
    - heure de lever du soleil idem
    - **plage théorique attendue** (coucher - 30 min → lever + 30 min)
    - **plage effective enregistrée** (extraite du journal du capteur : heure de premier déclenchement → heure de mise en veille)
    - écart résumé : ✅ conforme (écart < 5 min), ⚠ écart mineur (5-30 min), ❌ écart majeur (> 30 min)
- [ ] Si les coordonnées GPS sont absentes, l'encart est masqué avec une note discrète : « Renseignez les coordonnées GPS du point pour activer la vérification astronomique. » + lien direct vers la fiche site.
- [ ] Le calcul astronomique est fait **localement** par une bibliothèque (ex. `commons-suncalc` ou implémentation maison) — aucun appel réseau.
- [ ] Tests unitaires : sur une date et une coordonnée connues (ex. Aix-en-Provence, 22 avril 2026), vérifier que le coucher est calculé à ±2 min près de la valeur officielle.

**Parcours rattaché** : [P6](../Parcours%20utilisateurs/P6%20-%20Diagnostiquer%20le%20matériel.md), section « Cohérence horaires (calcul astronomique) »<br>
**Maquettes cibles** : [M-Passage](../Maquettes/M-Passage.md) (onglet Diagnostic, encart cohérence horaires)<br>
**Dépendances** : [E1.S3](E1%20-%20Gérer%20ses%20sites%20et%20points%20de%20suivi.md#e1s3), [E2.S1](E2%20-%20Importer%20et%20transformer%20une%20nuit.md#e2s1), [E6.S2](#e6s2)<br>
**Complexité** : ★★★ (moyen — bibliothèque astronomique + comparaison de plages + gestion conditionnelle de l'encart)<br>
**MoSCoW** : ⚪ COULD (idée Samuel, utile pour audit qualité mais pas critique au MVP)

---

## E6.S4 - Comparer le diagnostic avec un passage précédent du même enregistreur { #e6s4 }

**En tant que** [Karim](../Personas/Karim.md)

**Je veux** afficher côte à côte le diagnostic d'un passage et celui d'un passage antérieur du même enregistreur

**Afin de** repérer une dérive sur la durée (batterie qui faiblit nuit après nuit, sonde climatique qui décale) sans avoir à ouvrir 2 fenêtres et comparer mentalement

**Critères d'acceptation** :

- [ ] Depuis l'onglet « Diagnostic » d'un passage, un bouton « Comparer avec passage précédent » ouvre un sélecteur listant les autres passages **du même enregistreur** (matching par n° de série), ordonnés du plus récent au plus ancien.
- [ ] Au choix d'un passage de référence, l'écran bascule en mode comparaison avec deux colonnes : passage courant à gauche, passage de référence à droite.
- [ ] Les 3 encarts (T°/hygro, batterie, évènements) sont dupliqués côte à côte.
- [ ] Pour les courbes T°/hygro, possibilité d'**afficher les deux courbes superposées** sur un même graphe (toggle) pour faciliter la lecture de dérive.
- [ ] La comparaison fonctionne aussi entre deux passages de **points différents** si l'utilisateur le souhaite (utile pour comparer deux sites équipés du même type d'enregistreur).
- [ ] Bouton « Sortir de la comparaison » pour revenir au diagnostic mono-passage.

**Parcours rattaché** : [P6](../Parcours%20utilisateurs/P6%20-%20Diagnostiquer%20le%20matériel.md), étape 3<br>
**Maquettes cibles** : [M-Passage](../Maquettes/M-Passage.md) (variante diagnostic avec mode comparaison)<br>
**Dépendances** : [E6.S1](#e6s1), [E6.S2](#e6s2)<br>
**Complexité** : ★★ (simple — composition de 2 vues identiques + toggle de superposition de courbes)<br>
**MoSCoW** : ⚪ COULD (productivité Karim/Samuel ; pas dans le périmètre minimal de diagnostic)

---

## E6.S5 - Exporter le diagnostic d'un passage en CSV ou PDF { #e6s5 }

**En tant que** [Karim](../Personas/Karim.md)

**Je veux** pouvoir exporter le rapport de diagnostic d'une nuit dans un fichier autonome (CSV pour traitement ou PDF pour archive/partage)

**Afin de** transmettre ce diagnostic à un fabricant en cas de SAV, ou l'archiver dans mon rapport client

**Critères d'acceptation** :

- [ ] Bouton « Exporter le diagnostic » dans l'onglet « Diagnostic » avec choix de format : CSV ou PDF.
- [ ] **Export CSV** : un fichier unique avec sections distinctes (séparées par lignes vides) :
    - en-tête : site, point, n° passage, date, enregistreur n° série
    - séries T°/hygro (toutes les mesures du THLog)
    - tableau d'évènements anormaux (1 ligne par évènement)
    - synthèse batterie (début/fin/delta)
    - synthèse cohérence horaires (si activée)
- [ ] **Export PDF** : document mise en page lisible avec graphes (T°/hygro), tableau d'évènements, synthèses. Format A4 portrait.
- [ ] Le fichier produit porte un nom auto-généré au format `diagnostic_CarXXXXXX-AAAA-PassN-YY_aaaammjj.csv` ou `.pdf`.
- [ ] L'utilisateur choisit le dossier de destination via un sélecteur natif.
- [ ] Tests d'intégration : export sur un passage représentatif, vérification de la structure du CSV et de l'ouverture du PDF par un lecteur standard.

**Parcours rattaché** : [P6](../Parcours%20utilisateurs/P6%20-%20Diagnostiquer%20le%20matériel.md), étape 4<br>
**Maquettes cibles** : [M-Passage](../Maquettes/M-Passage.md) (bouton « Exporter le diagnostic » + modal de choix de format)<br>
**Dépendances** : [E6.S1](#e6s1), [E6.S2](#e6s2)<br>
**Complexité** : ★★ (simple — sérialisation CSV + génération PDF basique via bibliothèque type iText ou OpenPDF)<br>
**MoSCoW** : ⚪ COULD (export confort, pas critique au MVP)
