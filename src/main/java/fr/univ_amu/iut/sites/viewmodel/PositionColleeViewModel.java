package fr.univ_amu.iut.sites.viewmodel;

import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import fr.univ_amu.iut.sites.model.PropositionCarre;
import fr.univ_amu.iut.sites.model.VerdictProposition;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/// Le versant **« partir d'un lieu plutôt que d'un numéro »** de la modale de site (#4577).
///
/// ## Pourquoi une classe à part
///
/// Même motif que [CarreExistantViewModel], extrait en #3801 : ce concern ne partage aucun état avec la
/// saisie. Il a son texte, son verdict et son geste, et il ne lit du formulaire que le numéro qu'on lui
/// passe.
///
/// ## Pourquoi il n'est pas optionnel
///
/// `RechercheCarreExistant` et `ControleCarreStoc` sont `Optional` parce qu'ils ont besoin de la
/// plateforme. Celui-ci n'en a pas besoin : le carroyage national est embarqué, et le geste marche hors
/// connexion. C'est la décision D0 du chantier, et c'est ce qui la rend visible dans le code.
public final class PositionColleeViewModel {

    private final PropositionCarre proposition;

    /// Ce que l'observateur a collé. Le texte, pas une position : la lecture peut échouer, et son refus
    /// est un verdict comme un autre.
    private final StringProperty texte = new SimpleStringProperty(this, "texte", "");

    /// Ce que la dernière tentative a donné, avec sa gravité. Vide tant qu'on n'a rien demandé.
    private final ReadOnlyObjectWrapper<RetourOperation> retour =
            new ReadOnlyObjectWrapper<>(this, "retour", RetourOperation.AUCUN);

    /// Le numéro à déposer dans le champ des six chiffres, **vide** quand il n'y a rien à déposer :
    /// position sur une frontière, hors grille, ou texte illisible. Trois cas sur quatre ne proposent
    /// rien, et c'est voulu.
    private final ReadOnlyStringWrapper numeroPropose = new ReadOnlyStringWrapper(this, "numeroPropose", "");

    public PositionColleeViewModel(PropositionCarre proposition) {
        this.proposition = Objects.requireNonNull(proposition, "proposition");
        // Un verdict porte sur LE texte qui l'a demandé : dès qu'il change, il ne dit plus rien de ce
        // qui est à l'écran. Même règle que le verdict d'existence de [CarreExistantViewModel].
        texte.addListener((observable, avant, apres) -> oublier());
    }

    public StringProperty texte() {
        return texte;
    }

    public ReadOnlyObjectProperty<RetourOperation> retour() {
        return retour.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty numeroPropose() {
        return numeroPropose.getReadOnlyProperty();
    }

    /// Situe le texte collé, en sachant ce que le champ porte déjà.
    ///
    /// `numeroActuel` sert à dire ce qu'on remplace : un numéro tapé à la main est une intention, et
    /// l'écraser sans un mot ferait disparaître une divergence que l'observateur avait peut-être raison
    /// de tenir.
    public void situer(String numeroActuel) {
        VerdictProposition verdict = proposition.pour(texte.get());
        Optional<String> propose = verdict.numeroAProposer();
        numeroPropose.set(propose.orElse(""));
        retour.set(propose.map(numero -> aDeposer(numero, verdict, numeroActuel))
                .orElseGet(() -> RetourOperation.avertissement(verdict.message())));
    }

    /// Efface le verdict : il ne juge plus ce qui est à l'écran.
    public void oublier() {
        retour.set(RetourOperation.AUCUN);
        numeroPropose.set("");
    }

    private static RetourOperation aDeposer(String numero, VerdictProposition verdict, String numeroActuel) {
        if (numeroActuel != null && !numeroActuel.isBlank() && !numeroActuel.equals(numero)) {
            return RetourOperation.avertissement(
                    verdict.message() + " Le numéro " + numeroActuel + " qui s'y trouvait a été remplacé.");
        }
        if (numero.equals(numeroActuel)) {
            return RetourOperation.succes("Cette position confirme le carré " + numero + " déjà saisi.");
        }
        return RetourOperation.succes(verdict.message());
    }
}
