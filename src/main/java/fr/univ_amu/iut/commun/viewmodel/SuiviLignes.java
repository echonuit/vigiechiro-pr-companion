package fr.univ_amu.iut.commun.viewmodel;

import java.util.List;
import java.util.Optional;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/// Pilote d'une **table de suivi par unité** côté ViewModel : détient les [LigneSuivi] observables et
/// applique le cycle de vie reçu du modèle (plan établi → démarrée → progresse → terminée / échec).
/// Chaque événement cible sa ligne par **numéro** (le travail amont pouvant être parallèle, ils
/// arrivent dans le désordre) ; un numéro inconnu est ignoré sans erreur.
///
/// Socle **partagé** (génération des archives #820, suivi des fichiers d'import…) : une feature le
/// spécialise pour typer ses lignes et traduire ses événements métier (plan, unité terminée avec ses
/// données propres) en appels au pilote.
///
/// **Fil JavaFX** : ces méthodes mutent des collections/propriétés observables ; l'appelant (le
/// controller) les invoque sur le fil JavaFX fourni par le socle (`ExecuteurTache#surFilJavaFx()`, #1252),
/// comme le callback de progression global.
public class SuiviLignes<L extends LigneSuivi> {

    private final ObservableList<L> lignes = FXCollections.observableArrayList();
    private final ObservableList<L> lignesNonModifiables = FXCollections.unmodifiableObservableList(lignes);

    /// Vue **non modifiable** des lignes, à lier directement à la `TableView`.
    public ObservableList<L> lignes() {
        return lignesNonModifiables;
    }

    /// Vide la table (nouvelle opération ou réinitialisation de la vue).
    public void reinitialiser() {
        lignes.clear();
    }

    /// Remplace toutes les lignes (plan établi ou réhydratation) ; les spécialisations construisent
    /// leurs lignes à partir de leurs événements métier puis les posent ici.
    protected final void remplacerLignes(List<? extends L> nouvelles) {
        lignes.setAll(nouvelles);
    }

    /// L'unité `numero` commence : sa ligne passe « en cours ».
    public void demarrer(int numero) {
        ligne(numero).ifPresent(LigneSuivi::demarrer);
    }

    /// Avancement intra-unité : `faits` pas sur `total` pour l'unité `numero`.
    public void progresser(int numero, int faits, int total) {
        if (total > 0) {
            ligne(numero).ifPresent(l -> l.progresser(faits / (double) total));
        }
    }

    /// L'unité `numero` est traitée : sa ligne passe « terminée ».
    public void terminer(int numero) {
        ligne(numero).ifPresent(LigneSuivi::terminer);
    }

    /// Le traitement de l'unité `numero` a échoué : sa ligne passe « échec ».
    public void echouer(int numero, String raison) {
        ligne(numero).ifPresent(l -> l.echouer(raison));
    }

    /// Ligne portant `numero`, vide si inconnue (événement ignoré sans erreur).
    protected final Optional<L> ligne(int numero) {
        return lignes.stream().filter(l -> l.numero() == numero).findFirst();
    }
}
