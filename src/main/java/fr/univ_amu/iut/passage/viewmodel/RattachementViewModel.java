package fr.univ_amu.iut.passage.viewmodel;

import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.passage.model.DetailPassage;
import fr.univ_amu.iut.passage.model.ServicePassage;
import java.util.Objects;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleIntegerProperty;

/// ViewModel de la modale « Modifier le rattachement » (E2.S8) : corrige l'année ou le numéro de
/// passage d'un passage importé, sans changer de site/point.
///
/// Pur (aucun `javafx.scene` — règle ArchUnit `viewmodel_sans_javafx_ui`). Expose les deux champs
/// éditables ([#anneeProperty], [#numeroPassageProperty]), un **récapitulatif** réactif des
/// conséquences ([#recapProperty] : « X → Y, N séquences renommées ») et un message d'erreur. Le
/// carré et le code point (inchangés) sont fournis par la navigation : le `model`/`viewmodel` ne
/// dépend pas de `sites`. [#valider] délègue à [ServicePassage#modifierRattachement].
public class RattachementViewModel {

    private final ServicePassage service;
    private final IntegerProperty annee = new SimpleIntegerProperty(this, "annee");
    private final IntegerProperty numeroPassage = new SimpleIntegerProperty(this, "numeroPassage");
    private final ReadOnlyStringWrapper recap = new ReadOnlyStringWrapper(this, "recap", "");
    private final ReadOnlyStringWrapper messageErreur = new ReadOnlyStringWrapper(this, "messageErreur", "");

    /// Conditions de dépôt (météo + matériel du micro) éditées **dans la même modale** : la saisie de
    /// ces métadonnées VigieChiro se fait au moment où l'on modifie le passage, plutôt que sur l'écran
    /// M-Passage. Partage la ligne de message d'erreur de la modale.
    private final SaisiePassageConditions conditions;

    private Long idPassage;
    private String carre;
    private String codePoint;
    private int anneeActuelle;
    private int numeroActuel;
    private int nombreSequences;

    public RattachementViewModel(ServicePassage service) {
        this.service = Objects.requireNonNull(service, "service");
        this.conditions = new SaisiePassageConditions(service, messageErreur);
        annee.addListener((observable, avant, apres) -> majRecap());
        numeroPassage.addListener((observable, avant, apres) -> majRecap());
    }

    /// Sous-ViewModel des conditions de dépôt (météo + micro), lié aux champs de la modale.
    public SaisiePassageConditions conditions() {
        return conditions;
    }

    /// Initialise la modale sur le passage `idPassage` (carré et code point fournis par la
    /// navigation). Pré-remplit les champs avec les valeurs courantes lues via le service.
    public void ouvrirSur(Long idPassage, String carre, String codePoint) {
        this.idPassage = Objects.requireNonNull(idPassage, "idPassage");
        this.carre = Objects.requireNonNull(carre, "carre");
        this.codePoint = Objects.requireNonNull(codePoint, "codePoint");
        DetailPassage detail = service.detailPassage(idPassage);
        anneeActuelle = detail.annee();
        numeroActuel = detail.numeroPassage();
        nombreSequences = detail.nombreSequences();
        conditions.charger(idPassage, detail.meteo());
        messageErreur.set("");
        annee.set(detail.annee());
        numeroPassage.set(detail.numeroPassage());
        majRecap();
    }

    /// Applique le nouveau rattachement (année + n° saisis), après validation des bornes.
    ///
    /// @return `true` si l'opération a réussi (la vue peut fermer la modale) ; `false` sinon (saisie
    ///     invalide, ou échec opérationnel — R5, disque, base — dont le motif est dans
    ///     [#messageErreurProperty])
    public boolean valider() {
        if (numeroPassage.get() < 1) {
            messageErreur.set("Le numéro de passage doit être supérieur ou égal à 1.");
            return false;
        }
        if (annee.get() < 1000 || annee.get() > 9999) {
            messageErreur.set("L'année doit comporter quatre chiffres.");
            return false;
        }
        try {
            service.modifierRattachement(idPassage, new Prefixe(carre, annee.get(), numeroPassage.get(), codePoint));
            messageErreur.set("");
            return true;
        } catch (RuntimeException echec) {
            // Surface toute défaillance opérationnelle dans la modale (règle métier R5, disque, base)
            // plutôt que de la laisser échapper au gestionnaire d'action JavaFX (cf. PassageViewModel).
            messageErreur.set(echec.getMessage());
            return false;
        }
    }

    /// Applique **d'un bloc** le rattachement (année + n°) et les conditions de dépôt (météo + micro)
    /// saisis dans la modale. Enregistre d'abord les conditions (une saisie non numérique laisse la
    /// modale ouverte sans rien renommer sur le disque), puis délègue au rattachement [#valider] (qui,
    /// lui, renomme les séquences). Renvoie `true` si **tout** a réussi (la vue peut fermer la modale).
    public boolean appliquer() {
        if (!conditions.enregistrerMeteo() || !conditions.enregistrerMateriel()) {
            return false;
        }
        return valider();
    }

    private void majRecap() {
        if (carre == null) {
            recap.set("");
            return;
        }
        String avant = new Prefixe(carre, anneeActuelle, numeroActuel, codePoint).nomDossierSession();
        String apres = new Prefixe(carre, annee.get(), numeroPassage.get(), codePoint).nomDossierSession();
        if (avant.equals(apres)) {
            recap.set("Aucun changement de rattachement.");
        } else {
            recap.set("Rattachement : "
                    + avant
                    + " → "
                    + apres
                    + " — "
                    + nombreSequences
                    + " séquence(s) de la nuit seront renommées. Action irréversible.");
        }
    }

    public IntegerProperty anneeProperty() {
        return annee;
    }

    public IntegerProperty numeroPassageProperty() {
        return numeroPassage;
    }

    public ReadOnlyStringProperty recapProperty() {
        return recap.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty messageErreurProperty() {
        return messageErreur.getReadOnlyProperty();
    }
}
