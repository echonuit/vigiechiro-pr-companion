package fr.univ_amu.iut.importation.model;

import java.util.List;

/// Suivi **par fichier** d'un import (#947) : permet à l'IHM d'afficher une ligne par enregistrement
/// original avec son état (en attente → copie → transformation → terminé / échec), en complément de la
/// barre de progression globale (#33).
///
/// La transformation étant **parallèle** (#12), ces méthodes sont appelées depuis plusieurs fils et
/// **dans le désordre** ; chaque événement cible son fichier par son **numéro** (1..N, l'ordre du
/// plan), donc l'ordre d'arrivée n'importe pas. En import **multi-nuits**, un nouveau [#planEtabli]
/// est émis à chaque nuit : la table se replanifie nuit par nuit, en phase avec les libellés
/// « Nuit i/N · … » de la progression globale. L'implémentation IHM **doit relayer** ses mutations au
/// fil JavaFX (fourni par le socle, `ExecuteurTache#surFilJavaFx()`), comme le callback de
/// progression global.
public interface SuiviFichiers {

    /// Plan établi **avant toute écriture** de la nuit : le nom de chaque original à traiter, dans
    /// l'ordre des numéros (1..N), pour pré-remplir la table de lignes « en attente ».
    void planEtabli(List<String> noms);

    /// La copie protégée (R9) du fichier `numero` commence (mode conservation uniquement : en mode
    /// « sans copie », aucun événement de copie n'est émis).
    void copieDemarree(int numero);

    /// Le fichier `numero` est copié — ou sa copie fidèle a été retrouvée (reprise #231).
    void copieTerminee(int numero);

    /// La transformation R10/R11 du fichier `numero` commence.
    void transformationDemarree(int numero);

    /// Le fichier `numero` est transformé : ses séquences sont écrites.
    void fichierTermine(int numero);

    /// Le fichier `numero` est **rejeté** (#155 : illisible ou format invalide), sans abattre l'import.
    void fichierRejete(int numero, String raison);

    /// Suivi **inerte** (aucun affichage) : valeur par défaut des variantes sans IHM et des tests qui
    /// n'observent pas le détail par fichier.
    static SuiviFichiers inerte() {
        return new SuiviFichiers() {
            @Override
            public void planEtabli(List<String> noms) {}

            @Override
            public void copieDemarree(int numero) {}

            @Override
            public void copieTerminee(int numero) {}

            @Override
            public void transformationDemarree(int numero) {}

            @Override
            public void fichierTermine(int numero) {}

            @Override
            public void fichierRejete(int numero, String raison) {}
        };
    }
}
