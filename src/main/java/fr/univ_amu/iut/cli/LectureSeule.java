package fr.univ_amu.iut.cli;

/// Une commande qui **ne modifie rien** dans le dossier de travail, et se dispense donc du verrou
/// d'exclusivité (#3498).
///
/// ## Pourquoi la déclaration porte sur les LECTEURS
///
/// L'application graphique **réserve** le dossier pour toute sa durée ([VerrouWorkspace], #2731) ; la
/// CLI ne le demandait jamais et écrivait donc par-dessus. Le verrou est coopératif : il ne bloque
/// personne, il refuse à qui le demande.
///
/// Il fallait donc savoir quelles commandes écrivent. Aucun signal ne le dit de façon fiable : le nom
/// ne prouve rien, et « le service appelé sait écrire » classe `lister-sites` parmi les écrivains -
/// 55 des 71 commandes, mesuré.
///
/// ⚠️ Ce qui départage les deux formes de déclaration n'est pas leur précision, c'est **le sens dans
/// lequel une erreur se paie** :
///
/// - déclarer les **écrivains**, et en oublier un : son écriture échappe au verrou. Le défaut
///   persiste **en silence**, et rien ne le dira ;
/// - déclarer les **lecteurs**, et en oublier un : sa lecture est refusée pendant que l'application
///   est ouverte. **Visible, agaçante, sans danger** - et signalée le jour même.
///
/// La liste des lecteurs est aussi la plus stable : une commande qui lit le restera, alors qu'une
/// commande qui se met à écrire ne rappellera à personne qu'il faut la déclarer.
///
/// ## Ce que « lecture seule » veut dire ici
///
/// **Ne touche ni la base ni les dossiers de session.** Interroger le réseau, ou écrire **hors** du
/// dossier de travail, reste de la lecture seule : le verrou protège les données, pas le disque
/// entier. C'est le cas d'un CSV exporté à l'emplacement demandé (`lister-carres --sortie`), et c'est
/// aussi celui de la **configuration d'amorçage**, qui vit dans le dossier de configuration et se
/// protège toute seule, en s'écrivant d'un seul coup (#3507).
///
/// ⚠️ **Le journal fait exception, et il faut le dire** (#3575). `Cli.main` amorce la journalisation
/// avant tout, donc **toute** commande crée et écrit `<dossier de travail>/logs`, les vingt-trois
/// lectrices comprises. C'est voulu : un incident doit laisser une trace même sur une commande qui ne
/// fait que lire, et deux processus qui écrivent chacun ses lignes dans un fichier de journal ne se
/// corrompent pas - ce que deux processus qui écrivent la même base feraient.
///
/// La définition d'origine disait « ni les dossiers du dossier de travail », ce que **les vingt-trois
/// commandes qu'elle décrit contredisaient**. La décision n'a pas changé ; c'est sa formulation qui
/// promettait plus qu'elle ne tenait.
///
/// ⚠️ `emplacements` est lectrice pour cette raison, et pas seulement par technicité : elle sert à
/// **repointer** le dossier de travail. La verrouiller reviendrait à refuser de déménager à qui
/// déménage justement parce que la place actuelle est occupée ou abîmée.
///
/// ⚠️ La migration, elle, prend le verrou de son côté quand elle a quelque chose à appliquer
/// ([MigrationSchema]) : une commande de lecture sur une base à mettre à jour peut donc être refusée,
/// et c'est voulu - la mise à jour du schéma est une écriture.
public interface LectureSeule {}
