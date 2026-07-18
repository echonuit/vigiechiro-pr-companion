package fr.univ_amu.iut.commun.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.ConstructeurLienEspece;
import fr.univ_amu.iut.commun.model.EspeceIdentifiee;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.scene.control.MenuItem;

/// Action IHM **réutilisable** « Fiche de l'espèce » : fabrique l'item de menu contextuel qui ouvre,
/// dans le navigateur système, la fiche d'information d'une espèce identifiée. Réemployée à l'identique
/// par les vues espèces (Sons & validation #847, Analyse #848).
///
/// Combine le socle logique [ConstructeurLienEspece] (URL de fiche, sans réseau) et [OuvreurDeLien]
/// (ouverture externe, déjà en place pour « voir sur la carte »). Au clic, l'URL passe par une dernière
/// étape de **résolution** ([ResolveurFiche] : GBIF recherche → fiche, #922) exécutée hors du fil JavaFX
/// puis rouverte sur ce fil ([ExecuteurFiche]). Ne connaît que `commun` : les contrôleurs lui fournissent
/// l'[EspeceIdentifiee] extraite de leur propre modèle de ligne, sans coupler leurs features entre elles.
///
/// Quand aucune fiche n'est constructible (pseudo-taxons `noise`/`piaf`, couple sans binôme), l'item est
/// **désactivé** et son **libellé** en explique la cause : un [MenuItem] désactivé n'accueille pas de
/// tooltip (il ne reçoit plus le survol), on surface donc le motif dans le texte, comme les autres items
/// grisés du projet (#789).
public final class ActionFicheEspece {

    private static final String LIBELLE = "Fiche de l'espèce";
    private static final String SUFFIXE_SANS_FICHE = " (aucune fiche disponible)";

    private final ConstructeurLienEspece constructeur;
    private final OuvreurDeLien ouvreur;
    private final ResolveurFiche resolveur;
    private final ExecuteurFiche executeur;

    @Inject
    public ActionFicheEspece(
            ConstructeurLienEspece constructeur,
            OuvreurDeLien ouvreur,
            ResolveurFiche resolveur,
            ExecuteurFiche executeur) {
        this.constructeur = Objects.requireNonNull(constructeur, "constructeur");
        this.ouvreur = Objects.requireNonNull(ouvreur, "ouvreur");
        this.resolveur = Objects.requireNonNull(resolveur, "resolveur");
        this.executeur = Objects.requireNonNull(executeur, "executeur");
    }

    /// Fabrique un nouvel item de menu pour `espece` (cf. [#configurer] pour l'état posé). Pratique quand
    /// l'item est construit en code ; pour un item déclaré en FXML et réutilisé au fil de la sélection,
    /// préférer [#configurer].
    public MenuItem creerItem(EspeceIdentifiee espece) {
        MenuItem item = new MenuItem();
        configurer(item, espece);
        return item;
    }

    /// Pose l'état de `item` pour `espece`, **en place** (item déclaré en FXML, reconfiguré à chaque
    /// changement de sélection). Fiche disponible : item actif, libellé enrichi du nom vernaculaire,
    /// ouverture du lien au clic. Sinon : item désactivé, libellé explicatif, action retirée (clic
    /// inerte). Réversible : reconfigurer d'un taxon avec fiche vers un taxon sans fiche (et inversement)
    /// rétablit correctement le libellé, l'état grisé et l'action.
    public void configurer(MenuItem item, EspeceIdentifiee espece) {
        Optional<String> lien = constructeur.lienFiche(espece);
        if (lien.isEmpty()) {
            item.setText(LIBELLE + SUFFIXE_SANS_FICHE);
            item.setDisable(true);
            item.setOnAction(null);
            return;
        }
        item.setText(libelle(espece));
        item.setDisable(false);
        // Au clic : délègue au point d'entrée impératif [#ouvrir] (résolution éventuelle hors fil JavaFX).
        item.setOnAction(evenement -> ouvrir(espece));
    }

    /// Ouvre la fiche de `espece` dans le navigateur système **si** une fiche est constructible : une
    /// éventuelle résolution (GBIF recherche → fiche, #922) tourne hors du fil JavaFX, puis l'ouverture est
    /// reprise sur ce fil ([ExecuteurFiche]). Point d'entrée **impératif** partagé entre le menu contextuel
    /// ([#configurer]) et les autres gestes des vues espèces (double-clic, #1792) : une même logique
    /// d'ouverture, un seul endroit.
    ///
    /// **Rend compte** de ce qu'il advient, au lieu d'être simplement inerte quand aucune fiche n'est
    /// disponible (pseudo-taxons `noise`/`piaf`, couple sans binôme). Un menu peut griser son item et en
    /// afficher le motif ([#configurer]) ; un double-clic, lui, n'a pas d'état à montrer avant le geste,
    /// et son silence se lit comme une panne (#1834). L'appelant apprend donc que rien ne s'est ouvert et
    /// le signale avec le vocabulaire de son écran.
    ///
    /// @return `true` si une fiche a été ouverte, `false` si `espece` n'en a aucune
    public boolean ouvrir(EspeceIdentifiee espece) {
        Optional<String> lien = constructeur.lienFiche(espece);
        lien.ifPresent(url -> executeur.resoudrePuisOuvrir(() -> resolveur.resoudre(url), ouvreur::ouvrir));
        return lien.isPresent();
    }

    /// Ouvre la fiche de `espece` ; à défaut, **signale le motif** à `siAucuneFiche`, que l'écran présente
    /// comme il l'entend (bandeau, barre de statut…). Forme attendue du **double-clic**, qui n'a pas d'état
    /// à montrer avant le geste : sans motif, son silence se lit comme une panne (#1834, #1837).
    ///
    /// `espece` nulle (aucune ligne sous le curseur) : ni ouverture ni message.
    public void ouvrirOuSignaler(EspeceIdentifiee espece, Consumer<String> siAucuneFiche) {
        if (espece != null && !ouvrir(espece)) {
            siAucuneFiche.accept(motifAucuneFiche(espece));
        }
    }

    /// Motif présenté à l'utilisateur, qui **nomme le taxon** tel qu'il le lit dans la table ; à défaut son
    /// code, et en dernier recours une formule sans nom plutôt que des guillemets vides.
    private static String motifAucuneFiche(EspeceIdentifiee espece) {
        String nom = espece.nomVernaculaireFr();
        if (nom == null || nom.isBlank()) {
            nom = espece.codeTadarida();
        }
        return nom == null || nom.isBlank()
                ? "Aucune fiche disponible pour ce taxon."
                : "Aucune fiche disponible pour « " + nom + " ».";
    }

    private static String libelle(EspeceIdentifiee espece) {
        String nom = espece.nomVernaculaireFr();
        return nom == null || nom.isBlank() ? LIBELLE : LIBELLE + " (" + nom + ")";
    }
}
