# Constitution

Les règles non négociables de ce dépôt et du produit qu'il livre. Une règle ne figure ici que si
elle est **opposable** : quelque chose la refuse mécaniquement, ou son absence d'applicateur est
déclarée.

**Comment lire ce document.** Trente articles tiennent en une page ; ils suffisent à travailler.
Sous chacun vit sa **jurisprudence** : les décisions d'architecture qui l'ont produit, une par cas,
dans `dev-docs/decisions/`. On lit les articles ; on ne descend dans les cas que lorsqu'on en a
besoin. Chaque ADR déclare son article en en-tête, et un garde refuse celle qui n'en déclare pas.

**Un numéro manque, et c'est voulu.** La numérotation saute de A26 à A28. A27 existe dans la ligne
d'où vient cette constitution : il interdit un renvoi qui ne résout que dans un dépôt disparu, ce qui
n'a pas d'objet ici, où les renvois résolvent. Le numéro n'est pas réattribué : une ADR cite un
article par son numéro, et un numéro qui change de sens est pire qu'un numéro absent.

**Ce que la jurisprudence a révélé.** Les 194 ADR se replient sur vingt-trois articles. Les trois
autres n'ont produit aucune décision : la preuve qui précède l'affirmation (A1), la mutation qui
mesure dès qu'un comportement est complet (A8), et l'interdiction de supprimer un avertissement
(A10). Tous trois disent comment on *travaille* plutôt que ce que le produit *fait*, et tous trois
vivaient dans un fichier exclu du dépôt jusqu'à #4335. La mesure est rappelée ici plutôt que tue :
un article que rien ne tient est une dette, pas une règle.

---

## Titre I : la preuve

### A1 : La preuve précède l'affirmation

Aucune affirmation d'achèvement sans preuve fraîche. La commande a été lancée dans ce fil, sa
sortie a été lue, son code de sortie vérifié. Sauter une de ces étapes, c'est affirmer, pas
vérifier.

### A2 : Un garde est vu rouge sur sa propre mutation

Un dispositif vert n'est pas un dispositif vérifié. Avant de lui faire confiance, on casse à la
main exactement ce qu'il prétend attraper, et on le regarde rougir. Après toute réécriture du garde
ou de son sujet, on refait la mutation. Un garde qui ne peut pas rougir s'ancre autrement, et un
détecteur textuel s'exclut de son propre corpus.

### A3 : Un dispositif dit ce qu'il couvre, et ce qu'il n'a pas pu lire

Un garde déclare la couverture qu'il a, et distingue l'état qu'il tient de celui qu'il emprunte.
Une mesure dit ce qu'elle n'a pas pu lire. Un verdict ne juge que ce qu'on lui a demandé. Un
dispositif qui peut ne rien vérifier le dit.

### A4 : Un cas joué n'est pas un cas prouvé

Un test peut passer sur un écran qui n'a rien affiché. Ce qui se voit se contrôle sur ce que le
**produit** rend, pas sur ce que le test a reconstruit : une capture passe par le code de
production, un banc qui maximise tout ne montre pas ce qu'on livre, et la recette a trois états, pas
deux.

### A5 : La mesure fait foi, et dit d'où elle vient

Un relevé qui n'a pas ouvert les fichiers est une hypothèse. Un nombre se lit contre un référentiel
cité, un chiffre que le code sait recalculer ne s'écrit pas à la main, et un chiffre qu'il ignore ne
s'écrit pas du tout. Un inventaire ne se duplique pas, il se cite. La mesure fait foi en CI, pas sur
le poste.

### A6 : La vérification se déclare, sur trois niveaux

`certaine` nomme un test ou un script déterministe. `probable` nomme un script de suspects et son
cliquet. `humaine` nomme le motif de sa non-mécanisation. Un niveau `certaine` sans référence
nommée est refusé. Une capacité sait comment on la vérifie à la main ; une affirmation de sécurité
se lit, ou elle n'existe pas.

### A7 : Le test précède le code

Rouge, vert, refactor, autant de fois qu'il le faut. Sur un défaut, le premier test **reproduit**
le défaut avant qu'une ligne ne soit corrigée.

### A8 : La mutation mesure dès qu'un comportement est complet

Pas à la clôture. On cible des classes pures, on lance avec une phase, et on lit les survivants un
par un. Le pourcentage ne dit rien.

## Titre II : la dette

### A9 : La dette se tient par un cliquet, pas par un nettoyage

Une zone au plancher se garde par un **refus**. Une dette qu'on résorbe par tranches se tient par un
**cliquet** qui ne remonte jamais. Un zéro non gardé ne reste pas zéro, et un cliquet s'apprend en
l'appliquant.

### A10 : Jamais de suppression d'avertissement

Ni `@SuppressWarnings`, ni `//NOPMD`. Un avertissement se traite en refactorant.

### A11 : L'assumé se déclare, il ne se contourne pas

Une règle absente et assumée vaut mieux qu'une règle contournée. Une perte consentie s'annonce, un
secret laissé en clair se dit, une exclusion nomme son repreneur ou c'est un trou. Ce qui est
assumé et écrit reste discutable ; ce qui est contourné en silence ne l'est plus.

## Titre III : rendre compte

### A12 : Rendre compte avant de conclure, et aucun échec silencieux

Un dispositif rapporte ce qu'il a vu avant de conclure. La sévérité se décide à l'émission. Un audit
rend tout écart visible, mais un état normal ne crie pas, et un geste qui n'aboutit pas le dit.

### A13 : Un refus dit ce qui manque, et ne conseille que le vérifié

Le refus nomme ce qui manque, la surface dit quoi faire. Un refus ne conseille que ce qu'il a
vérifié applicable, et un refus définitif se réarme sur ce qui a levé sa cause, sur rien d'autre.
Un audit qui ne peut pas trancher montre sans juger.

### A14 : Un état n'est pas un compte rendu, et ils ne partagent pas de canal

Une barre de statut dit où l'on en est, pas si c'est bien ou mal. Rendre compte se fait au bandeau ;
le modal est réservé à l'irréversible. Un message montré à l'utilisateur se compose en un seul
endroit, et ce n'est pas une vue.

## Titre IV : l'état et la donnée

### A15 : Un état est observé, une décision se prend, ni l'un ni l'autre ne se déduit

« Archivé » est un état observé, pas un statut. L'origine d'une donnée est un état porté, pas
déduit. Une décision d'affichage se prend, la forme d'un dépôt se choisit, un défaut de plateforme
se sonde. Une dérivation automatique ne défait jamais une saisie manuelle.

### A16 : L'information se porte dans le type, pas dans la prudence des phrases

Un retour d'API est un type scellé. Un invariant tenu par la base se double d'un refus dans le code.
Une clé de critère est un contrat de sérialisation : un concept, un endroit, un renommage sans
migration.

### A17 : Ne rien inventer, ne rien effacer, n'écrire que ce qui est déclaré

Écrire sur la plateforme : ne rien inventer, ne rien effacer. On n'écrit jamais plus que ce qui est
déclaré. Un traitement en lot compose des gestes unitaires, il n'en invente pas un nouveau. Une
restauration vérifie en place, un effacement dit son contrat dans son nom, un script publié ne se
modifie plus.

## Titre V : le produit

### A18 : L'utilisateur possède ses fichiers, l'application observe

L'application observe la disponibilité des fichiers, elle ne les archive pas. La seule configuration
qui vive hors de la base est celle qui dit où la base se trouve.

### A19 : Toute capacité métier est offerte aussi en ligne de commande

Parité CLI et IHM. La parité d'une sortie machine est de **dire**, pas de retirer. Un groupe de
commandes techniques est borné, et ne dispense pas des commandes métier.

### A20 : Le produit ne dépend pas de son outillage

Ce que le produit embarque lui appartient : une classe CSS a une seule feuille pour maison, les
constantes d'identité d'un installeur sont des constantes, et un outil compose son injecteur depuis
la racine.

### A21 : La nuit, du crépuscule à l'aube, est l'unité de traitement

La nuit se lit du crépuscule à l'aube, pas de minuit à minuit, et elle porte le fuseau de son site,
pas celui du poste. Une nuit hors protocole se filtre au lieu de se fondre dans le lot.

### A22 : Une feature est un plugin désactivable, et rien ne cycle entre elles

Les ponts entre features passent par un port dans `commun`. Les dialogues bloquants sont des ports
injectables. Un cycle de vie qu'on peut oublier est mal placé.

## Titre VI : ce qui se montre

### A23 : Ce qui s'affiche est embarqué et montré en entier, et rien ne s'offre qui ne se montre

La typographie est embarquée, pas empruntée à la machine, et ce que l'application affiche tient dans
la police embarquée. Une barre d'actions plie, elle ne tronque pas ; un fil d'Ariane élide des
segments, il ne rogne pas des libellés. Un écran n'offre pas un lieu qu'il ne montre pas.

## Titre VII : le dépôt

### A24 : La langue du dépôt est le français

Commits, issues, PR, documentation et commentaires. Les mots-clés de fermeture GitHub et les titres
structurels des outils restent en anglais, faute de quoi ils cessent de fonctionner. Le sujet d'un
commit est une syntaxe, pas une phrase française.

### A25 : Le travail de branche est isolé

Le dépôt d'origine ne sert qu'à récupérer et tester `main`. Plusieurs sessions travaillent en
parallèle sous un compte partagé. Un check requis ne gouverne pas les PR, il gouverne la branche, et
le code tiers ne s'exécute pas avec les droits de publication.

### A26 : La provenance se déclare

Chaque décision porte qui l'a produite et qui l'a relue. L'assistance par agents est déclarée, pas
déduite. L'identité de distribution est distincte de celle de l'auteur et de celle de la plateforme.

### A28 : Un avertissement se dit en mots

Un pictogramme d'alerte n'apporte pas l'information : il annonce qu'il y en a une. Posé sur 1 187
lignes et dans 459 fichiers, il ne distingue plus rien. Ce qui doit alerter se dit dans la phrase, ou
dans l'encart que le format prévoit. Le pictogramme ne subsiste que là où il est le contenu montré :
une maquette qui rend ce que l'écran affiche, un message que le programme émet.

### A29 : L'ergonomie se rattache à une heuristique nommée

Une décision qui porte sur l'usage - ce qu'un écran montre, ce qu'il refuse, ce qu'il fait attendre -
déclare par le champ `heuristiques:` de son en-tête la ou les heuristiques qu'elle sert. Le
vocabulaire est **clos** : dix heuristiques de Nielsen, l'affordance et le signifiant de Norman, six
lois de la Gestalt, tenues par `dev-docs/ergonomie/heuristiques.md`. Une clé hors vocabulaire est
refusée. Une décision rattachée à A12, A13, A14, A15, A18, A19, A23 ou A28 qui ne déclare rien est un
**suspect sous cliquet**, pas un refus : le rattachement se fait par tranches, chaque décision ouverte
et lue, jamais par ressemblance de mots. Une heuristique que rien ne sert est dite dans le rapport,
sans faire rougir : c'est un manque à connaître, pas une faute à corriger.

### A30 : Le code dit ce qu'il fait, la javadoc dit son contrat, l'ADR dit pourquoi

Trois endroits, un rôle chacun. Le **code** porte l'intention : un nom qui la dit, une fonction qui
tient dans un écran, un type qui rend l'erreur impossible. Un commentaire qui explique un nom est un
nom à changer ; un commentaire qui paraphrase la ligne suivante s'enlève. La **javadoc** dit le
contrat - ce qu'on fournit, ce qu'on obtient, ce qui peut être nul - et s'adresse à qui appelle.
L'**ADR** dit pourquoi, et la javadoc la **cite** au lieu de la redire.

Ce qui est caduc s'enlève : `git log` garde l'histoire pour qui la cherche, et une javadoc dont une
moitié est périmée ne se lit plus du tout. Un bloc de plus de huit lignes de prose est un suspect
sous cliquet, dans le code de production ; un garde, lui, doit dire ce qu'il vérifie.

### A31 : La prose visible se relit à l'humaniseur

Toute prose qu'un humain lira hors de l'échange qui l'a produite - javadoc, documentation, ADR,
libellés d'interface et de ligne de commande, messages de commit, corps d'issue et de pull request -
passe la grille de la compétence `humaniser` avant d'être **publiée**, par un commit comme sur la
forge. Les sept tics de `CONTRIBUTING.md` en sont le sous-ensemble
opposable : la grille sert à relire, les sept servent à refuser. Rien ne mécanise le reste, et c'est
dit : aucun motif textuel ne décide si une emphase informe, ni si une javadoc paraphrase la
signature qu'elle surmonte.

---

<!-- matrice engendree : ne pas editer a la main -->

## Matrice de traçabilité

Engendrée depuis les en-têtes des ADR par `scripts/methode/matrice-constitution.py`, et gardée par lui.

| Article | Jurisprudence | Dont mécanisée | Tenu par |
|---|---:|---:|---|
| A1 · La preuve précède l'affirmation | 4 | 1 | `.github/scripts/verifie-decisions-du-tournage-connecte.sh`, `.github/scripts/verifie-jeton-vivant.sh`, `.github/scripts/revoque-jeton.sh` |
| A2 · Un garde est vu rouge sur sa propre mutation | 6 | 4 | `src/test/bats/cli.bats`, `src/test/java/fr/univ_amu/iut/architecture/AnnonceDesMutationsTest.java`, `.github/scripts/mesure-duree-portail.sh`, et 1 autre |
| A3 · Un dispositif dit ce qu'il couvre, et ce qu'il n'a pas pu lire | 15 | 14 | `.github/scripts/veille-contrat-api.sh`, `AnalyseViewTest#colonne_commune_sur_la_table_des_observations`, `SiteEditRechercheCarreTest#un_verdict_arrive_en_retard_est_ecarte`, et 13 autres |
| A4 · Un cas joué n'est pas un cas prouvé | 22 | 15 | `ApercuFxElisionTest#bouton_tronque_refuse`, `ActiviteViewTest#l_export_image_redessine_un_graphe_reellement_dessine`, `scripts/adr/3053-capture-libelle.py`, et 12 autres |
| A5 · La mesure fait foi, et dit d'où elle vient | 24 | 16 | `GenerationCartesSDCliquetTest#chaque_spec_produit_la_pathologie_attendue`, `ReferentielActiviteTest#precise_mais_peu_fiable_ecartee`, `EspecesPrioritairesReferentielTest#marque_toutes_les_prioritaires_connues`, et 12 autres |
| A6 · La vérification se déclare, sur trois niveaux | 3 | 2 | `DocumentationAJourTest#la_verification_declaree_par_une_adr_existe_vraiment`, `EcritureAtomiqueTest#creation_restreinte` |
| A7 · Le test précède le code | 1 | 1 | `BancDesClipsTest#une_classe_filmee_neuve_declare_son_banc` |
| A8 · La mutation mesure dès qu'un comportement est complet | 0 | 0 | **relecture seule** |
| A9 · La dette se tient par un cliquet, pas par un nettoyage | 10 | 4 | `scripts/adr/2843-tiret-cadratin.py`, `PatronDuCliquetTest#tout_cliquet_passe_par_le_patron`, `scripts/adr/verifie_scripts.py`, et 6 autres |
| A10 · Jamais de suppression d'avertissement | 0 | 0 | **relecture seule** |
| A11 · L'assumé se déclare, il ne se contourne pas | 7 | 0 | **relecture seule** |
| A12 · Rendre compte avant de conclure, et aucun échec silencieux | 8 | 4 | `scripts/adr/0008-echec-silencieux.py`, `.github/scripts/verifie-secret-winget.sh`, `RetourOperationTest#les_deux_causes_ne_se_melangent_pas`, et 2 autres |
| A13 · Un refus dit ce qui manque, et ne conseille que le vérifié | 9 | 8 | `scripts/adr/2635-refus-sans-surface.py`, `AuditDepartementDuPointTest#legitime_et_suspecte_indiscernables`, `FiltresLieuTest#le_point_est_filtrable`, et 6 autres |
| A14 · Un état n'est pas un compte rendu, et ils ne partagent pas de canal | 8 | 2 | `CompteRenduChiffreTest#ventilation_non_exhaustive_refusee`, `CauseLisibleTest#l_enveloppe_de_reflexion_ne_masque_pas_la_panne`, `scripts/adr/3947-message-enveloppe.py` |
| A15 · Un état est observé, une décision se prend, ni l'un ni l'autre ne se déduit | 21 | 16 | `DecisionsRespecteesTest#archive_n_est_pas_un_statut_de_workflow`, `RapprochementNuitsOpportunistesTest#ne_demarque_jamais_une_saisie_manuelle`, `PolitiqueReessaiTest#refus_definitif_ne_reessaie_pas`, et 13 autres |
| A16 · L'information se porte dans le type, pas dans la prudence des phrases | 13 | 11 | `DecisionsRespecteesTest#l_echelle_de_severite_a_quatre_niveaux_dans_l_ordre`, `ClesCriteresTest#aucune_cle_publiee_deux_fois`, `CritereLieuTest#un_carre_une_entree_deux_etiquettes`, et 8 autres |
| A17 · Ne rien inventer, ne rien effacer, n'écrire que ce qui est déclaré | 28 | 20 | `MoteurTraitementGroupeTest#annulation_apres_le_passage_courant`, `RestaurationCompleteTest#restauration_sur_une_autre_machine`, `EmpreinteMigrationsTest#script_modifie_apres_coup_fait_refuser`, et 32 autres |
| A18 · L'utilisateur possède ses fichiers, l'application observe | 4 | 2 | `WorkspaceTest#resolu_lit_la_configuration_persistee`, `ServiceImportReferenceTest#reference_pose_identite_et_placeholder` |
| A19 · Toute capacité métier est offerte aussi en ligne de commande | 5 | 4 | `ArchitectureTest#lecture_brute_reservee_au_groupe_api`, `ExportSyntheseCsvTest#referentiel_indisponible_conserve_les_colonnes`, `FiltresActiviteTest#ce_qui_qualifie_rend_vide_sans_refuser`, et 1 autre |
| A20 · Le produit ne dépend pas de son outillage | 6 | 6 | `DecisionsRespecteesTest#l_installeur_windows_porte_ses_constantes_d_identite`, `DoublonsFeuillesDeStyleTest#chaque_classe_a_une_seule_feuille`, `ArchitectureTest#produit_sans_outillage`, et 3 autres |
| A21 · La nuit, du crépuscule à l'aube, est l'unité de traitement | 6 | 3 | `AgregationActiviteTest#l_export_date_ses_lignes_par_la_nuit_biologique`, `NatureNuitTest#un_passage_marque_est_une_participation_opportuniste`, `CorrespondanceParticipationTest#le_depot_ne_depend_pas_du_poste` |
| A22 · Une feature est un plugin désactivable, et rien ne cycle entre elles | 4 | 2 | `DecisionsRespecteesTest#aucun_cycle_entre_les_features`, `scripts/adr/0010-dialogue-hors-port.py`, `NavigateurTest#relibeller_ne_reabonne_pas` |
| A23 · Ce qui s'affiche est embarqué et montré en entier, et rien ne s'offre qui ne se montre | 17 | 11 | `scripts/adr/0035-pictogramme-caractere.py`, `scripts/adr/0037-slot-actions-hbox.py`, `scripts/adr/2493-modale-suit-croissance.py`, et 12 autres |
| A24 · La langue du dépôt est le français | 2 | 2 | `.github/scripts/verifie-titre-pr.sh`, `scripts/methode/verifie-controle-du-titre.py` |
| A25 · Le travail de branche est isolé | 3 | 1 | `.github/scripts/verifie-epinglage.sh` |
| A26 · La provenance se déclare | 3 | 2 | `DecisionsRespecteesTest#l_installeur_porte_l_identite_echonuit`, `DocumentationAJourTest#une_adr_recente_porte_le_numero_de_son_chantier` |
| A28 · Un avertissement se dit en mots | 1 | 0 | `scripts/adr/4366-avertissement-en-pictogramme.py` |
| A29 · L'ergonomie se rattache à une heuristique nommée | 1 | 0 | `scripts/adr/verifie_okf.py` |
| A30 · Le code dit ce qu'il fait, la javadoc dit son contrat, l'ADR dit pourquoi | 5 | 1 | `scripts/adr/4359-javadoc-narratif.py`, `scripts/adr/4359-blocs-relus.py`, `scripts/adr/4395-renvois-en-javadoc.py`, et 2 autres |
| A31 · La prose visible se relit à l'humaniseur | 4 | 1 | `.github/scripts/verifie-corps-pr.sh`, `scripts/adr/4783-traces-d-outil.py` |

**3 article(s) sur 30 ne sont tenus que par la relecture.** C'est la liste des chantiers de garde restants, et elle se lit comme un inventaire, pas comme une fatalité.

- A8 · La mutation mesure dès qu'un comportement est complet
- A10 · Jamais de suppression d'avertissement
- A11 · L'assumé se déclare, il ne se contourne pas

<!-- fin de la matrice engendree -->
