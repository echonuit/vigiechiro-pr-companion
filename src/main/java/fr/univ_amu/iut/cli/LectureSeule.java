package fr.univ_amu.iut.cli;

/// Une commande qui **ne modifie rien** dans le dossier de travail, et se dispense donc du verrou
/// d'exclusivité (#3498) que l'application graphique, elle, tient pour toute sa durée (#2731).
///
/// Ne touche ni la base ni les dossiers de session. Écrire **hors** du dossier de travail reste de
/// la lecture seule, la configuration d'amorçage comprise (#3507) : le verrou protège les données,
/// pas le disque. Le journal fait exception (#3575), toute commande y écrivant dès l'amorçage ; et
/// la migration prend le verrou de son côté quand elle a quelque chose à appliquer
/// ([MigrationSchema]), si bien qu'une lecture sur une base à mettre à jour peut être refusée.
public interface LectureSeule {}
