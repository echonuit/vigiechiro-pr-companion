package fr.univ_amu.iut.importation.viewmodel;

import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.importation.model.NuitAImporter;
import fr.univ_amu.iut.importation.model.ResultatImportMultiNuits;
import fr.univ_amu.iut.importation.model.ServiceImport;
import fr.univ_amu.iut.importation.model.SuiviFichiers;
import fr.univ_amu.iut.importation.viewmodel.ImportationViewModel.DemandeImportNuits;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.ListChangeListener;

/// Coordination de l'import **multi-nuits** côté ViewModel (#…), extraite de [ImportationViewModel]
/// (Extract Class) sur le modèle de [ControleNumeroPassage] : l'orchestrateur reste mince (façade), ce
/// collaborateur porte l'**auto-numérotation** des nuits et la préparation/exécution de la demande.
///
/// Il **s'abonne lui-même** au rattachement (point / année / **n° de passage**) et à la table des nuits
/// (ajout/retrait, (dé)coche) pour recalculer, à chaque changement, les n° de passage proposés : la table
/// **suit le n° de passage du formulaire** (source unique #…), chaque nuit **incluse** (ordre des dates)
/// recevant un n° **consécutif** à partir de ce n° saisi. À chaque changement de contexte, il **pré-remplit**
/// ce n° de base avec le premier **bloc de N n° consécutifs libres** (N = nombre de nuits), comblant les
/// trous sans casser la consécutivité (p. ex. 1,3,5,7 existants + 3 nuits → 8,9,10). Il expose la
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
    private final ReadOnlyStringWrapper avertissement = new ReadOnlyStringWrapper(this, "avertissement", "");

    CoordinationNuits(
            ServiceImport serviceImport,
            InspectionImportViewModel inspection,
            RattachementImportViewModel rattachement) {
        this.serviceImport = Objects.requireNonNull(serviceImport, "serviceImport");
        this.inspection = Objects.requireNonNull(inspection, "inspection");
        this.rattachement = Objects.requireNonNull(rattachement, "rattachement");

        // Changement de **contexte** (point / année / passage en multi-nuits) → pré-remplir le n° de base
        // avec le premier BLOC de N n° consécutifs libres, puis renuméroter la table. Saisie du **n° de
        // passage** → la table suit (renumérotation seule, pas de re-proposition). (Dé)coche d'une nuit →
        // renumérotation seule (on ne réécrase pas le n° de base choisi).
        rattachement.pointSelectionneProperty().addListener((obs, ancien, nouveau) -> surChangementContexte());
        rattachement.anneeProperty().addListener((obs, ancien, nouveau) -> surChangementContexte());
        rattachement.numeroPassageProperty().addListener((obs, ancien, nouveau) -> renumeroter());
        // `plusieursNuits` passe à `true` **après** le `setAll` de la table (donc après le déclenchement du
        // listener de liste ci-dessous) : sans cet abonnement, une carte multi-nuits inspectée alors que le
        // rattachement est déjà complet garderait des n° à « — » jusqu'à ce qu'on retouche le rattachement.
        // C'est aussi ici que le n° de base est (re)proposé avec la bonne taille de bloc (= nb de nuits).
        inspection.plusieursNuitsProperty().addListener((obs, ancien, nouveau) -> surChangementContexte());
        inspection.nuits().addListener((ListChangeListener<NuitVM>) changement -> {
            while (changement.next()) {
                for (NuitVM ajoutee : changement.getAddedSubList()) {
                    ajoutee.inclureProperty().addListener((obs, ancien, nouveau) -> renumeroter());
                }
            }
            renumeroter();
        });
    }

    /// Changement de contexte : (re)propose un **n° de passage de base** puis renumérote. Le n° proposé est
    /// le premier **bloc de N n° consécutifs libres** (N = nombre de nuits incluses en multi-nuits, sinon
    /// 1), pour combler les trous tout en respectant la consécutivité (p. ex. 1,3,5,7 existants + 3 nuits →
    /// 8,9,10). L'utilisateur peut ensuite librement modifier le n° (la table suivra).
    private void surChangementContexte() {
        proposerNumeroDeBase();
        renumeroter(); // garantit la mise à jour même si le n° proposé est identique au n° courant
    }

    /// Pré-remplit le n° de passage du formulaire avec le premier bloc de N n° consécutifs libres (source
    /// unique #… : la table suit ce n°). Sans point sélectionné, ne fait rien.
    private void proposerNumeroDeBase() {
        PointDEcoute point = rattachement.pointSelectionneProperty().get();
        if (point == null) {
            return;
        }
        int incluses =
                (int) inspection.nuits().stream().filter(NuitVM::estIncluse).count();
        int taille = inspection.plusieursNuits() ? Math.max(1, incluses) : 1;
        int base = serviceImport.prochainBlocPassagesLibre(
                point.id(), rattachement.anneeProperty().get(), taille);
        if (base >= 1) {
            rattachement.numeroPassageProperty().set(base);
        }
    }

    /// Validité de la numérotation multi-nuits (entre dans `peutImporter` de l'orchestrateur).
    ReadOnlyBooleanProperty numerotationValideProperty() {
        return numerotationValide.getReadOnlyProperty();
    }

    /// Motif de blocage (#801) à afficher sous la table des nuits quand la numérotation multi-nuits est
    /// invalide (« aucune nuit incluse » ou « n° déjà pris »), sur le modèle de l'avertissement mono-nuit.
    /// Vide quand tout va bien.
    public ReadOnlyStringProperty avertissementProperty() {
        return avertissement.getReadOnlyProperty();
    }

    /// Recalcule les **n° de passage proposés** : la nuit **incluse** (ordre des dates) part du **n° de
    /// passage saisi dans le formulaire** (source unique #…) et les suivantes reçoivent des n° **consécutifs**
    /// ; les nuits exclues repassent à 0. Met à jour la validité (≥ 1 nuit incluse **et** tous les n° proposés
    /// libres, R5). Sans rattachement complet ou hors multi-nuits, ne propose rien.
    private void renumeroter() {
        if (!inspection.plusieursNuits()) {
            numerotationValide.set(true); // non pertinent : le pré-contrôle mono-nuit (#108) fait foi
            avertissement.set("");
            return;
        }
        if (!rattachement.estComplet()) {
            inspection.nuits().forEach(nuit -> nuit.definirNumeroPassagePropose(0));
            numerotationValide.set(false);
            // Le rattachement incomplet est déjà signalé par sa propre section : pas de doublon ici.
            avertissement.set("");
            return;
        }
        Long idPoint = rattachement.idPointSelectionne();
        int annee = rattachement.prefixeCourant().annee();
        int numero = rattachement.numeroPassageProperty().get(); // base = n° du formulaire (la table le suit)
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
        if (incluses < 1) {
            avertissement.set("Incluez au moins une nuit à importer.");
        } else if (!tousLibres) {
            avertissement.set("Un ou plusieurs numéros de passage proposés sont déjà utilisés.");
        } else {
            avertissement.set("");
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
        return executer(demande, progres, jeton, SuiviFichiers.inerte());
    }

    /// Variante **multi-nuits** avec suivi par fichier (#947) : le plan est replanifié à chaque nuit, en
    /// phase avec la progression agrégée « Nuit i/N · … ». **Ne lit aucune `Property`** : sûr sur un fil
    /// d'arrière-plan.
    public ResultatImportMultiNuits executer(
            DemandeImportNuits demande, Consumer<Progression> progres, JetonAnnulation jeton, SuiviFichiers suivi) {
        return serviceImport.importerNuits(
                demande.dossier(),
                demande.idPoint(),
                demande.prefixeBase(),
                demande.nuits(),
                demande.conserverOriginaux(),
                progres,
                jeton,
                suivi);
    }
}
