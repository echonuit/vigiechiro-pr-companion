package fr.univ_amu.iut.commun.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.ConstructeurLienEspece;
import fr.univ_amu.iut.commun.model.EspeceIdentifiee;
import java.util.Objects;
import java.util.Optional;
import javafx.scene.control.MenuItem;

/// Action IHM **réutilisable** « Fiche de l'espèce » : fabrique l'item de menu contextuel qui ouvre,
/// dans le navigateur système, la fiche d'information d'une espèce identifiée. Réemployée à l'identique
/// par les vues espèces (Sons & validation #847, Analyse #848).
///
/// Combine le socle logique [ConstructeurLienEspece] (résolution de l'URL, sans réseau) et
/// [OuvreurDeLien] (ouverture externe, déjà en place pour « voir sur la carte »). Ne connaît que
/// `commun` : les contrôleurs lui fournissent l'[EspeceIdentifiee] extraite de leur propre modèle de
/// ligne, sans coupler leurs features entre elles.
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

    @Inject
    public ActionFicheEspece(ConstructeurLienEspece constructeur, OuvreurDeLien ouvreur) {
        this.constructeur = Objects.requireNonNull(constructeur, "constructeur");
        this.ouvreur = Objects.requireNonNull(ouvreur, "ouvreur");
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
        String url = lien.get();
        item.setOnAction(evenement -> ouvreur.ouvrir(url));
    }

    private static String libelle(EspeceIdentifiee espece) {
        String nom = espece.nomVernaculaireFr();
        return nom == null || nom.isBlank() ? LIBELLE : LIBELLE + " (" + nom + ")";
    }
}
