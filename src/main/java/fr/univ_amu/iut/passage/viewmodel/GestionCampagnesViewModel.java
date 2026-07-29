package fr.univ_amu.iut.passage.viewmodel;

import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import fr.univ_amu.iut.passage.model.Campagne;
import fr.univ_amu.iut.passage.model.ServiceCampagne;
import java.util.Objects;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/// ViewModel de la modale **« Gérer les campagnes »** (#2630) : créer, renommer et supprimer une
/// campagne depuis l'application.
///
/// Il n'apporte **aucune règle** : [ServiceCampagne] les porte toutes, y compris ses refus (nom
/// obligatoire, campagne introuvable). Ce ViewModel les traduit en [RetourOperation] pour la surface,
/// et tient la liste à jour après chaque écriture : c'est tout ce qui manquait pour que la
/// fonctionnalité soit atteignable sans terminal.
///
/// Le service est ici **obligatoire**, contrairement à [SelectionCampagne] : cette modale ne s'ouvre
/// que si la fonctionnalité `campagne` est active. Une modale de gestion derrière un `Optional` vide
/// n'aurait rien à gérer.
///
/// Pur (`javafx.beans` / `javafx.collections` uniquement, règle ArchUnit `viewmodel_sans_javafx_ui`).
public class GestionCampagnesViewModel {

    /// Ouverture des messages nommant une campagne, mutualisée (PMD `AvoidDuplicateLiterals`).
    private static final String CAMPAGNE = "Campagne « ";

    private final ServiceCampagne service;
    private final ObservableList<Campagne> campagnes = FXCollections.observableArrayList();
    private final ObjectProperty<Campagne> selection = new SimpleObjectProperty<>(this, "selection");
    private final ReadOnlyObjectWrapper<RetourOperation> retour =
            new ReadOnlyObjectWrapper<>(this, "retour", RetourOperation.AUCUN);

    public GestionCampagnesViewModel(ServiceCampagne service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    /// (Re)charge la liste des campagnes. La sélection courante est **conservée si elle existe
    /// encore** : renommer une campagne ne doit pas déplacer le curseur de l'utilisateur.
    public void charger() {
        Long idSelectionne = selection.get() == null ? null : selection.get().id();
        campagnes.setAll(service.listerCampagnes());
        selection.set(campagnes.stream()
                .filter(campagne -> campagne.id().equals(idSelectionne))
                .findFirst()
                .orElse(null));
    }

    /// Crée une campagne, la liste est rechargée et la nouvelle campagne **sélectionnée** : on vient de
    /// la créer, c'est celle sur laquelle on veut agir ensuite.
    ///
    /// @param annee `null` pour l'année courante (le service décide, il porte l'horloge)
    public void creer(String nom, Integer annee, String commentaire) {
        executer(() -> {
            Campagne creee = service.creerCampagne(nom, annee, commentaire);
            charger();
            selection.set(campagnes.stream()
                    .filter(campagne -> campagne.id().equals(creee.id()))
                    .findFirst()
                    .orElse(null));
            return RetourOperation.succes(CAMPAGNE + creee.nom() + " » créée.");
        });
    }

    /// Renomme la campagne sélectionnée (ou change son année ou son commentaire).
    public void modifier(String nom, int annee, String commentaire) {
        Campagne cible = selection.get();
        if (cible == null) {
            retour.set(RetourOperation.info("Choisissez d'abord une campagne à modifier."));
            return;
        }
        executer(() -> {
            Campagne modifiee = service.modifierCampagne(cible.id(), nom, annee, commentaire);
            charger();
            return RetourOperation.succes(CAMPAGNE + modifiee.nom() + " » enregistrée.");
        });
    }

    /// Supprime la campagne sélectionnée. Les passages rattachés sont **détachés**, jamais effacés :
    /// le message le redit après coup, parce que c'est la question que se pose celui qui vient de
    /// cliquer.
    public void supprimer() {
        Campagne cible = selection.get();
        if (cible == null) {
            retour.set(RetourOperation.info("Choisissez d'abord une campagne à supprimer."));
            return;
        }
        long rattaches = passagesRattaches(cible);
        executer(() -> {
            service.supprimerCampagne(cible.id());
            selection.set(null);
            charger();
            return RetourOperation.succes(
                    "Campagne « " + cible.nom() + " » supprimée. " + phraseDetachement(rattaches));
        });
    }

    /// Nombre de passages rattachés à `campagne` : ce que la confirmation doit annoncer **avant** de
    /// supprimer.
    public long passagesRattaches(Campagne campagne) {
        Objects.requireNonNull(campagne, "campagne");
        return service.compterPassagesRattaches(campagne.id());
    }

    /// Phrase du détachement, au singulier ou au pluriel, ou silence s'il n'y avait rien à détacher.
    ///
    /// Publique parce que la **confirmation** l'emploie avant l'acte et le retour après : deux endroits,
    /// une seule formulation, sinon les deux finissent par diverger.
    public static String phraseDetachement(long rattaches) {
        if (rattaches == 0) {
            return "Aucun passage n'y était rattaché.";
        }
        return rattaches == 1
                ? "Le passage qui y était rattaché a été détaché, pas effacé."
                : "Les " + rattaches + " passages qui y étaient rattachés ont été détachés, pas effacés.";
    }

    /// Exécute une écriture et traduit son issue en [RetourOperation]. Les refus du service
    /// ([fr.univ_amu.iut.commun.model.RegleMetierException], nom vide) sont des **réponses**, pas des
    /// pannes : ils s'affichent, ils ne remontent pas.
    private void executer(Ecriture ecriture) {
        try {
            retour.set(ecriture.appliquer());
        } catch (RuntimeException refus) {
            retour.set(RetourOperation.erreur(refus));
        }
    }

    /// Année proposée par défaut à la création : celle de l'horloge du service, jamais celle de la
    /// machine : une capture doit rendre la même image d'une année sur l'autre.
    public int anneeParDefaut() {
        return service.anneeParDefaut();
    }

    /// Efface le retour affiché (l'utilisateur ferme le bandeau).
    public void effacerRetour() {
        retour.set(RetourOperation.AUCUN);
    }

    /// Campagnes existantes, de la plus récente à la plus ancienne (ordre du service).
    public ObservableList<Campagne> campagnes() {
        return campagnes;
    }

    /// Campagne sélectionnée dans la liste, `null` si aucune.
    public ObjectProperty<Campagne> selectionProperty() {
        return selection;
    }

    /// Retour de la dernière opération, [RetourOperation#AUCUN] tant qu'il n'y en a pas eu.
    public ReadOnlyObjectProperty<RetourOperation> retourProperty() {
        return retour.getReadOnlyProperty();
    }

    /// Une écriture qui rend le message à afficher si elle aboutit.
    @FunctionalInterface
    private interface Ecriture {
        RetourOperation appliquer();
    }
}
