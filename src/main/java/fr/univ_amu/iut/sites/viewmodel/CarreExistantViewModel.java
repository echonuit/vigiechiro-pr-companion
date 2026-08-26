package fr.univ_amu.iut.sites.viewmodel;

import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import fr.univ_amu.iut.sites.model.RapatriementCarre;
import fr.univ_amu.iut.sites.model.RechercheCarreExistant;
import fr.univ_amu.iut.sites.model.SouhaitDeclaration;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;

/// Le versant **« ce carré existe-t-il là-bas, et peut-on le récupérer ? »** de la modale de site
/// (#3458, #3806).
///
/// ## Pourquoi une classe à part
///
/// Ce concern ne partage **aucun état** avec la saisie : il a ses deux ports optionnels, son verdict et
/// son geste, et il ne lit du formulaire que le numéro qu'on lui passe. Le garder dans
/// [SiteEditViewModel] y entretenait une cohésion de façade - deux moitiés cousues par le seul fait
/// d'habiter la même classe.
///
/// L'extraction a été **déclenchée par une mesure** : en retirant de `preparerCreation` des
/// réinitialisations qui ne servaient à rien (#3801), la cohésion mesurée du ViewModel est tombée sous
/// le seuil du portail qualité. Le remède n'était pas de taire l'avertissement - le dépôt l'interdit -
/// mais de constater ce qu'il disait : la classe portait deux sujets.
///
/// ## Ce qu'il garantit
///
/// **Une absence de réponse n'est pas une réponse** : un verdict [RechercheCarreExistant.Verdict.Indisponible]
/// ne dit jamais « ce carré est libre » (ADR 3458). Et **un verdict ne juge que le numéro qui l'a
/// demandé** : il s'oublie dès que la saisie change, et une réponse arrivée après une correction est
/// écartée.
public final class CarreExistantViewModel {

    /// « Ce carré existe-t-il déjà ? » (#3458). **Optionnel**, car il a besoin de la plateforme : absent
    /// (injecteurs partiels, feature de connexion éteinte), la vérification n'est simplement pas offerte
    /// et la déclaration reste entière. Même montage que `ControleCarreStoc`.
    private final Optional<RechercheCarreExistant> recherche;

    /// « Récupérer ce carré » (#3806). **Optionnel** pour la même raison : il interroge la plateforme.
    /// Absent, le geste n'est pas offert et la déclaration reste entière.
    private final Optional<RapatriementCarre> rapatriement;

    /// Ce que la plateforme a répondu sur le carré saisi, avec sa gravité. Vide tant qu'on n'a rien
    /// demandé.
    private final ReadOnlyObjectWrapper<RetourOperation> retour =
            new ReadOnlyObjectWrapper<>(this, "retour", RetourOperation.AUCUN);

    /// Le carré cherché existe sur la plateforme : il y a donc quelque chose à **récupérer** (#3806).
    /// Faux tant qu'on n'a pas demandé, faux si le carré est libre, et remis à faux dès que le numéro
    /// change - le geste ne survit pas au verdict qui l'a ouvert.
    private final ReadOnlyBooleanWrapper recuperable = new ReadOnlyBooleanWrapper(this, "recuperable", false);

    public CarreExistantViewModel(
            Optional<RechercheCarreExistant> recherche, Optional<RapatriementCarre> rapatriement) {
        this.recherche = Objects.requireNonNull(recherche, "recherche");
        this.rapatriement = Objects.requireNonNull(rapatriement, "rapatriement");
    }

    /// Ce qu'une interrogation rapporte : le verdict **et le numéro qui l'a demandé** (#3458).
    ///
    /// Le numéro voyage avec le verdict parce que la réponse revient **plus tard**, sur une modale que
    /// l'utilisateur a pu modifier entre-temps. Sans lui, rien ne permettrait de savoir que ce verdict
    /// ne juge plus ce qui est à l'écran.
    ///
    /// @param numeroInterroge le numéro de carré tel qu'il était au départ de la requête
    /// @param verdict ce que la plateforme en a dit
    public record ResultatRechercheCarre(String numeroInterroge, RechercheCarreExistant.Verdict verdict) {

        /// Aucune réponse exploitable pour `numeroInterroge` : panne technique, plutôt que refus de la
        /// plateforme (celui-là est déjà un [RechercheCarreExistant.Verdict.Indisponible] rendu par le
        /// modèle).
        public static ResultatRechercheCarre indisponible(String numeroInterroge) {
            return new ResultatRechercheCarre(numeroInterroge, new RechercheCarreExistant.Verdict.Indisponible());
        }
    }

    /// La vérification est-elle **installée** (#3458) ? Faux hors de l'application complète : la modale
    /// n'affiche alors pas le geste, plutôt qu'un bouton mort.
    public boolean disponible() {
        return recherche.isPresent();
    }

    /// Demande à la plateforme si `numeroCarre` y existe déjà.
    ///
    /// **Bloquant** (réseau) : à appeler hors du fil JavaFX, puis passer le résultat à [#appliquer].
    public ResultatRechercheCarre chercher(String numeroCarre) {
        if (recherche.isEmpty()) {
            return ResultatRechercheCarre.indisponible(numeroCarre);
        }
        return new ResultatRechercheCarre(numeroCarre, recherche.get().chercher(numeroCarre));
    }

    /// Applique un résultat de [#chercher] aux propriétés observables, **sur le fil JavaFX**.
    ///
    /// Un résultat qui porte sur un **autre numéro** que `numeroAffiche` est **écarté** : l'appel a duré,
    /// et la saisie a changé pendant ce temps. L'afficher quand même mettrait un avertissement sous un
    /// carré qu'il ne juge pas.
    public void appliquer(ResultatRechercheCarre resultat, String numeroAffiche) {
        if (!resultat.numeroInterroge().equals(numeroAffiche)) {
            return;
        }
        retour.set(new RetourOperation(
                resultat.verdict().message(), resultat.verdict().severite()));
        // Le geste suit le verdict : on ne propose de récupérer que ce qui est là-bas, et seulement si
        // le rapatriement est installé (sinon le bouton serait mort).
        recuperable.set(
                rapatriement.isPresent() && resultat.verdict() instanceof RechercheCarreExistant.Verdict.DejaDeclare);
    }

    /// Récupère le carré depuis la plateforme. **Bloquant** (réseau) : à appeler hors du fil JavaFX.
    public RapatriementCarre.Resultat rapatrier(SouhaitDeclaration souhait) {
        if (rapatriement.isEmpty()) {
            return new RapatriementCarre.Resultat.Indisponible(
                    "la récupération de carré n'est pas disponible dans cette configuration.");
        }
        return rapatriement.get().rapatrier(souhait);
    }

    /// Affiche le compte rendu du rapatriement, **sur le fil JavaFX**.
    public void appliquerRapatriement(RapatriementCarre.Resultat resultat) {
        retour.set(new RetourOperation(resultat.message(), resultat.severite()));
        // Une panne **ne referme pas** le geste, et ne rouvre pas la déclaration.
        //
        // Le geste ne se referme que lorsqu'il n'y a effectivement plus rien à récupérer : le carré
        // existe sous un autre protocole. Sur une plateforme injoignable, on sait toujours que le carré
        // est là-bas - le verdict vient de le dire - et la panne peut être passagère. Rouvrir « Créer »
        // dans cet état inviterait à fabriquer le doublon que ce chantier existe pour éviter.
        //
        // Trouvé en regardant une capture, pas par un test : l'écran montrait « Créer » redevenu bleu
        // après un échec réseau.
        if (!(resultat instanceof RapatriementCarre.Resultat.Indisponible)) {
            recuperable.set(false);
        }
    }

    /// Le verdict ne juge plus ce qui est à l'écran : on l'oublie, geste compris.
    public void oublier() {
        retour.set(RetourOperation.AUCUN);
        recuperable.set(false);
    }

    /// Ce que la plateforme a dit du carré saisi, avec sa gravité.
    public ReadOnlyObjectProperty<RetourOperation> retourProperty() {
        return retour.getReadOnlyProperty();
    }

    /// Y a-t-il un carré à récupérer, c'est-à-dire un verdict « il existe déjà » encore valable (#3806) ?
    public ReadOnlyBooleanProperty recuperable() {
        return recuperable.getReadOnlyProperty();
    }
}
