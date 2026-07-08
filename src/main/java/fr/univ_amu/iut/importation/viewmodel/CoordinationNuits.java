package fr.univ_amu.iut.importation.viewmodel;

import fr.univ_amu.iut.importation.model.JetonAnnulation;
import fr.univ_amu.iut.importation.model.NuitAImporter;
import fr.univ_amu.iut.importation.model.Progression;
import fr.univ_amu.iut.importation.model.ResultatImportMultiNuits;
import fr.univ_amu.iut.importation.model.ServiceImport;
import fr.univ_amu.iut.importation.viewmodel.ImportationViewModel.DemandeImportNuits;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.collections.ListChangeListener;

/// Coordination de l'import **multi-nuits** côté ViewModel (#…), extraite de [ImportationViewModel]
/// (Extract Class) sur le modèle de [ControleNumeroPassage] : l'orchestrateur reste mince (façade), ce
/// collaborateur porte l'**auto-numérotation** des nuits et la préparation/exécution de la demande.
///
/// Il **s'abonne lui-même** au rattachement (point / année) et à la table des nuits (ajout/retrait,
/// (dé)coche) pour recalculer, à chaque changement, les n° de passage proposés : chaque nuit **incluse**
/// (ordre des dates) reçoit un n° **consécutif** à partir du prochain n° libre du point. Il expose la
/// **validité** de cette numérotation ([#numerotationValideProperty()]) que l'orchestrateur compose dans
/// son `peutImporter`.
///
/// VM agnostique de l'IHM (règle ArchUnit `viewmodel_sans_javafx_ui`) : seuls `javafx.beans` et
/// `javafx.collections` sont importés, jamais `javafx.scene`.
public final class CoordinationNuits {

    private final ServiceImport serviceImport;
    private final InspectionImportViewModel inspection;
    private final RattachementImportViewModel rattachement;

    /// Validité de la numérotation : au moins une nuit incluse **et** tous les n° proposés libres (R5).
    /// Non pertinente hors multi-nuits (`true`).
    private final ReadOnlyBooleanWrapper numerotationValide =
            new ReadOnlyBooleanWrapper(this, "numerotationValide", true);

    CoordinationNuits(
            ServiceImport serviceImport,
            InspectionImportViewModel inspection,
            RattachementImportViewModel rattachement) {
        this.serviceImport = Objects.requireNonNull(serviceImport, "serviceImport");
        this.inspection = Objects.requireNonNull(inspection, "inspection");
        this.rattachement = Objects.requireNonNull(rattachement, "rattachement");

        // Recalcul de la numérotation quand le rattachement change, quand la table des nuits est
        // repeuplée (nouvelle inspection) et quand l'utilisateur (dé)coche une nuit.
        rattachement.pointSelectionneProperty().addListener((obs, ancien, nouveau) -> renumeroter());
        rattachement.anneeProperty().addListener((obs, ancien, nouveau) -> renumeroter());
        // `plusieursNuits` passe à `true` **après** le `setAll` de la table (donc après le déclenchement du
        // listener de liste ci-dessous) : sans cet abonnement, une carte multi-nuits inspectée alors que le
        // rattachement est déjà complet garderait des n° à « — » jusqu'à ce qu'on retouche le rattachement.
        inspection.plusieursNuitsProperty().addListener((obs, ancien, nouveau) -> renumeroter());
        inspection.nuits().addListener((ListChangeListener<NuitVM>) changement -> {
            while (changement.next()) {
                for (NuitVM ajoutee : changement.getAddedSubList()) {
                    ajoutee.inclureProperty().addListener((obs, ancien, nouveau) -> renumeroter());
                }
            }
            renumeroter();
        });
    }

    /// Validité de la numérotation multi-nuits (entre dans `peutImporter` de l'orchestrateur).
    ReadOnlyBooleanProperty numerotationValideProperty() {
        return numerotationValide.getReadOnlyProperty();
    }

    /// Recalcule les **n° de passage proposés** : chaque nuit **incluse** (ordre des dates) reçoit un n°
    /// **consécutif** à partir du prochain n° libre du point ; les nuits exclues repassent à 0. Met à jour
    /// la validité (≥ 1 nuit incluse **et** tous les n° proposés libres, R5). Sans rattachement complet ou
    /// hors multi-nuits, ne propose rien.
    private void renumeroter() {
        if (!inspection.plusieursNuits()) {
            numerotationValide.set(true); // non pertinent : le pré-contrôle mono-nuit (#108) fait foi
            return;
        }
        if (!rattachement.estComplet()) {
            inspection.nuits().forEach(nuit -> nuit.definirNumeroPassagePropose(0));
            numerotationValide.set(false);
            return;
        }
        Long idPoint = rattachement.idPointSelectionne();
        int annee = rattachement.prefixeCourant().annee();
        int numero = serviceImport.prochainNumeroPassageLibre(idPoint, annee);
        int incluses = 0;
        boolean tousLibres = true;
        for (NuitVM nuit : inspection.nuits()) {
            if (nuit.estIncluse()) {
                nuit.definirNumeroPassagePropose(numero);
                if (serviceImport.numeroPassageDejaUtilise(idPoint, annee, numero)) {
                    tousLibres = false;
                }
                numero++;
                incluses++;
            } else {
                nuit.definirNumeroPassagePropose(0);
            }
        }
        numerotationValide.set(incluses >= 1 && tousLibres);
    }

    /// Capture les nuits **incluses** (avec leur n° de passage proposé) et le préfixe de base commun dans
    /// un instantané immuable, sur le fil JavaFX.
    public DemandeImportNuits preparerDemande(Path dossier, boolean conserverOriginaux) {
        List<NuitAImporter> nuits = new ArrayList<>();
        for (NuitVM nuit : inspection.nuits()) {
            if (nuit.estIncluse()) {
                nuits.add(new NuitAImporter(nuit.numeroPassagePropose(), nuit.nuit()));
            }
        }
        return new DemandeImportNuits(
                dossier, rattachement.idPointSelectionne(), rattachement.prefixeCourant(), nuits, conserverOriginaux);
    }

    /// Exécute la demande via [ServiceImport#importerNuits] (un passage par nuit incluse). **Ne lit aucune
    /// `Property`** : sûr sur un fil d'arrière-plan.
    public ResultatImportMultiNuits executer(
            DemandeImportNuits demande, Consumer<Progression> progres, JetonAnnulation jeton) {
        return serviceImport.importerNuits(
                demande.dossier(),
                demande.idPoint(),
                demande.prefixeBase(),
                demande.nuits(),
                demande.conserverOriginaux(),
                progres,
                jeton);
    }
}
