package fr.univ_amu.iut.sites.viewmodel;

import fr.univ_amu.iut.commun.model.validation.ValidateurCodePoint;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import fr.univ_amu.iut.sites.model.CodePointLibre;
import fr.univ_amu.iut.sites.model.ControleCarreStoc;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.PointVoisin;
import fr.univ_amu.iut.sites.model.ServiceCommunes;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.VerdictCarre;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/// ViewModel de la **modale d'ajout / d'édition d'un point d'écoute** (M-Site-detail).
///
/// Porte les champs saisis (code, descriptif, latitude, longitude) en propriétés modifiables,
/// auxquelles la vue se lie de façon bidirectionnelle. La logique de présentation y est
/// entièrement testable sans IHM :
///  - validité du code (R2) via le validateur partagé [ValidateurCodePoint] ;
///  - validité des coordonnées GPS (optionnelles ; latitude −90..90, longitude −180..180,
///    virgule décimale tolérée) ;
///  - [#peutEnregistrer()] combine ces validités pour piloter l'état du bouton de validation.
///
/// La création délègue à [ServiceSites#ajouterPoint] et l'édition à [ServiceSites#modifierPoint] :
/// tous deux rejouent R2 + l'unicité du code dans le site (en excluant le point courant en
/// édition), pour ne jamais court-circuiter le service vers le DAO. Les refus métier
/// (code déjà pris…) sont restitués dans [#retourProperty()] et l'enregistrement renvoie
/// `false` (la modale reste ouverte).
public class PointEditViewModel {

    private final ServiceSites service;

    /// Tenue à jour de la commune des points (#2791) : sollicitée après un enregistrement réussi,
    /// hors du fil JavaFX (cf. [#resoudreCommune]).
    private final ServiceCommunes communes;

    /// Contrôle du carré STOC (#733) : **optionnel**, car il a besoin de la plateforme. Absent (feature de
    /// connexion éteinte), le contrôle ne se fait simplement pas : la saisie manuelle reste entière.
    private final Optional<ControleCarreStoc> controleCarre;

    private final StringProperty code = new SimpleStringProperty(this, "code", "");
    private final StringProperty description = new SimpleStringProperty(this, "description", "");
    /// La position, saisie dans un seul champ et lue comme celle du site (#5688).
    private final PositionSaisie position = new PositionSaisie();
    private final ReadOnlyStringWrapper titre = new ReadOnlyStringWrapper(this, "titre", "");
    private final ReadOnlyStringWrapper libelleBouton = new ReadOnlyStringWrapper(this, "libelleBouton", "+ Ajouter");
    /// Compte rendu de la dernière tentative d'enregistrement, avec sa sévérité (#1917). Il s'appelait
    /// `messageErreur` : la sévérité vivait dans son **nom**, ce qui l'empêchait de porter autre chose
    /// qu'un échec. Or un champ mal rempli n'est pas une panne, c'est un guidage.
    private final ReadOnlyObjectWrapper<RetourOperation> retour =
            new ReadOnlyObjectWrapper<>(this, "retour", RetourOperation.AUCUN);

    /// Ce que la grille STOC dit de la position saisie, avec sa gravité (#733, #2159) : vide tant
    /// qu'on ne sait rien.
    private final ReadOnlyObjectWrapper<RetourOperation> retourCarre =
            new ReadOnlyObjectWrapper<>(this, "retourCarre", RetourOperation.AUCUN);

    /// Vrai quand [#messageCarre] est une **alerte** (carré divergent, position hors grille) plutôt qu'une
    /// confirmation : la vue le colore en conséquence.

    private final BooleanBinding codeValide;
    private final BooleanBinding peutEnregistrer;

    private Long idSite;
    private Long idPointEnEdition;

    /// Le point que le dernier [#enregistrer] réussi a écrit : la cible de [#resoudreCommune].
    private Long idDernierPointEnregistre;

    /// L'intention de publier ce point dès son enregistrement (#3458) : la case, son gris, son
    /// verdict.
    private final IntentionPublication intentionPublication;

    /// Carré **déclaré par le site** courant : c'est lui que la grille STOC vient confirmer ou contredire.
    private String carreDuSite;

    public PointEditViewModel(
            ServiceSites service,
            ServiceCommunes communes,
            Optional<ControleCarreStoc> controleCarre,
            PublicationDepuisLaFiche publication) {
        this.service = Objects.requireNonNull(service, "service");
        this.communes = Objects.requireNonNull(communes, "communes");
        this.controleCarre = Objects.requireNonNull(controleCarre, "controleCarre");
        this.intentionPublication =
                new IntentionPublication(Objects.requireNonNull(publication, "publication"), this::gpsRenseigne);
        codeValide = Bindings.createBooleanBinding(() -> ValidateurCodePoint.estValide(code.get()), code);
        peutEnregistrer = codeValide.and(position.valide());
        // Le motif du gris suit la saisie : on peut cocher la case, puis effacer la position.
        position.texte().addListener((observable, avant, apres) -> intentionPublication.recalculer());
        position.texte().addListener((observable, avant, apres) -> signalerLeVoisin());
    }

    /// Configure la modale en **mode création** d'un point pour le site donné.
    public void preparerCreation(Site site) {
        Objects.requireNonNull(site, "site");
        this.idSite = site.id();
        this.idPointEnEdition = null;
        this.carreDuSite = site.numeroCarre();
        reinitialiserChamps();
        // Proposé, pas imposé : le Z suivant est la règle du portail pour un point libre (#5688), et les
        // points systématiques A1 à H2 restent à nommer à la main tant que #5608 n'est pas tranchée.
        pointsDuSite = service.listerPoints(site.id());
        code.set(CodePointLibre.suivant(
                pointsDuSite.stream().map(PointDEcoute::code).toList()));
        titre.set("Nouveau point d'écoute · Carré " + site.numeroCarre());
        libelleBouton.set("+ Ajouter");
        intentionPublication.aLaCreation(site.id());
    }

    /// Configure la modale en **mode édition** : champs pré-remplis depuis le point existant.
    public void preparerEdition(Site site, PointDEcoute point) {
        Objects.requireNonNull(site, "site");
        Objects.requireNonNull(point, "point");
        this.idSite = site.id();
        this.idPointEnEdition = point.id();
        this.carreDuSite = site.numeroCarre();
        intentionPublication.aLEdition();
        pointsDuSite = List.of();
        code.set(point.code());
        description.set(point.description() == null ? "" : point.description());
        if (point.latitude() == null || point.longitude() == null) {
            position.texte().set("");
        } else {
            position.placer(point.latitude(), point.longitude());
        }
        retour.set(RetourOperation.AUCUN);
        titre.set("Modifier le point " + point.code() + " · Carré " + site.numeroCarre());
        libelleBouton.set("Modifier");
    }

    /// Tente d'enregistrer le point (création ou édition).
    ///
    /// @return `true` si l'enregistrement a réussi (la vue peut fermer la modale) ; `false` si une
    ///     règle métier a refusé l'opération (le motif est dans [#retourProperty()])
    public boolean enregistrer() {
        if (!peutEnregistrer.get()) {
            return false;
        }
        try {
            Optional<double[]> coordonnees = position.coordonnees();
            Double lat = coordonnees.map(c -> c[0]).orElse(null);
            Double lon = coordonnees.map(c -> c[1]).orElse(null);
            String desc = description.get().isBlank() ? null : description.get();
            PointDEcoute enregistre;
            if (idPointEnEdition == null) {
                enregistre = service.ajouterPoint(idSite, code.get(), lat, lon, desc);
            } else {
                enregistre = service.modifierPoint(idPointEnEdition, idSite, code.get(), lat, lon, desc);
            }
            idDernierPointEnregistre = enregistre.id();
            retour.set(RetourOperation.AUCUN);
            return true;
        } catch (RuntimeException refus) {
            retour.set(RetourOperation.erreur(refus));
            return false;
        }
    }

    /// Résout et mémorise la **commune** du point que le dernier [#enregistrer] réussi a écrit
    /// (#2791). **Bloquant** (réseau) : à appeler hors du fil JavaFX, comme [#controlerCarre].
    /// Best-effort : sans effet tant que rien n'a été enregistré, silencieux hors ligne (le point
    /// reste « en attente », le rattrapage comblera).
    public void resoudreCommune() {
        if (idDernierPointEnregistre != null) {
            communes.mettreAJour(idDernierPointEnregistre);
        }
    }

    /// La case « publier ensuite » a-t-elle lieu d'être sur cet écran (#3458) ? Voir
    /// L'intention de publier le point à sa création (#3458), à laquelle la vue se lie directement : une
    /// couche de délégation ne ferait que recopier trois accesseurs, et le seuil God-class de cette
    /// classe n'a pas de place à leur donner.
    public IntentionPublication publication() {
        return intentionPublication;
    }

    /// Cf. [IntentionPublication#pointAPublier(Long)]. Reste ici : l'identifiant du point enregistré
    /// appartient à ce ViewModel.
    public Optional<Long> pointAPublier() {
        return intentionPublication.pointAPublier(idDernierPointEnregistre);
    }

    /// Les deux coordonnées sont-elles lisibles ? Seul ce ViewModel sait lire ses champs de saisie ;
    /// [IntentionPublication] le lui demande plutôt que d'en tenir une copie.
    ///
    /// Il **s'appuie sur les validités déjà calculées** au lieu de réanalyser. La version précédente
    /// rappelait `parserCoordonnee`, qui **lève** sur une saisie inanalysable - et comme cette méthode
    /// est appelée depuis un écouteur, à chaque frappe, une lettre parasite jetait un
    /// `NumberFormatException` non attrapé sur le fil JavaFX. Taper « lat 43,5 » ou coller un texte
    /// suffisait. `coordonneeValide` attrapait pourtant l'exception deux méthodes plus loin : la garde
    /// existait, ce chemin-ci passait à côté.
    ///
    /// Trouvé par une mutation destinée à éprouver un autre test (#4232) : c'est le hasard d'un mutant
    /// qui a nommé un défaut que personne ne cherchait.
    private boolean gpsRenseigne() {
        return position.coordonnees().isPresent();
    }

    public StringProperty codeProperty() {
        return code;
    }

    /// Le champ unique « Position » (#5688) : latitude puis longitude, lu comme à la déclaration du site.
    public StringProperty positionProperty() {
        return position.texte();
    }

    /// Le motif d'une position illisible ou hors des limites, vide sinon.
    public ReadOnlyObjectProperty<RetourOperation> retourPositionProperty() {
        return position.retour();
    }

    /// Un point du même site à 40 m au plus de la position saisie, en création (#5688). Un avertissement :
    /// il nomme le voisin et n'empêche pas d'enregistrer.
    private final ReadOnlyObjectWrapper<RetourOperation> retourVoisin =
            new ReadOnlyObjectWrapper<>(this, "retourVoisin", RetourOperation.AUCUN);

    /// Les points du site à l'ouverture d'une création ; vide en édition, où le point serait son voisin.
    private List<PointDEcoute> pointsDuSite = List.of();

    public ReadOnlyObjectProperty<RetourOperation> retourVoisinProperty() {
        return retourVoisin.getReadOnlyProperty();
    }

    private void signalerLeVoisin() {
        retourVoisin.set(position.coordonnees()
                .flatMap(lue -> PointVoisin.lePlusProche(lue[0], lue[1], pointsDuSite))
                .map(voisin -> RetourOperation.avertissement(voisin.avertissement()))
                .orElse(RetourOperation.AUCUN));
    }

    /// Écrit une position dans le champ, sous la forme qu'il relit : ce que fait le marqueur qu'on glisse.
    public void placer(double latitude, double longitude) {
        position.placer(latitude, longitude);
    }

    public StringProperty descriptionProperty() {
        return description;
    }

    public ReadOnlyStringProperty titreProperty() {
        return titre.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty libelleBoutonProperty() {
        return libelleBouton.getReadOnlyProperty();
    }

    /// Compte rendu de la dernière tentative d'enregistrement, rendu par le bandeau partagé (ADR 0023).
    /// [RetourOperation#AUCUN] en nominal.
    public ReadOnlyObjectProperty<RetourOperation> retourProperty() {
        return retour.getReadOnlyProperty();
    }

    /// Efface le retour (l'utilisateur a lu le bandeau et le ferme).
    public void effacerRetour() {
        retour.set(RetourOperation.AUCUN);
    }

    /// Validité du code de point (R2) : pilote le surlignage du champ dans la vue.
    public BooleanBinding codeValide() {
        return codeValide;
    }

    /// Conjonction des validités : pilote l'activation du bouton de validation.
    public BooleanBinding peutEnregistrer() {
        return peutEnregistrer;
    }

    /// Confronte la position saisie à la **grille STOC officielle** (#733). **Bloquant** (réseau) : à
    /// appeler hors du fil JavaFX, et à conclure par [#appliquerControleCarre].
    ///
    /// Rend [VerdictCarre.Indisponible] (donc le silence) dès qu'il n'y a rien à contrôler : coordonnées
    /// incomplètes, ou plateforme hors d'atteinte. Le contrôle est un **confort**, jamais une condition de
    /// saisie.
    public VerdictCarre controlerCarre() {
        Optional<double[]> saisie = coordonneesValides();
        if (controleCarre.isEmpty() || saisie.isEmpty() || carreDuSite == null) {
            return new VerdictCarre.Indisponible();
        }
        double[] coordonnees = saisie.get();
        return controleCarre.get().confronter(carreDuSite, coordonnees[0], coordonnees[1]);
    }

    /// Applique un verdict de [#controlerCarre] aux propriétés observables, **sur le fil JavaFX**.
    public void appliquerControleCarre(VerdictCarre verdict) {
        retourCarre.set(new RetourOperation(verdict.message(), verdict.severite()));
    }

    /// Ce que la grille STOC dit de la position saisie, **avec sa gravité**. Retour vide s'il n'y a rien
    /// à dire (hors ligne, contrôle indisponible).
    ///
    /// Remplace un couple `message` + `alerte` : le booléen ne distinguait pas une **confirmation** d'un
    /// **silence**, donc la vue peignait « ce point tombe bien dans le carré » du même gris qu'une absence
    /// de réponse. La sévérité vient du modèle (#2159), la vue la rend par [LibelleRetour].
    public ReadOnlyObjectProperty<RetourOperation> retourCarreProperty() {
        return retourCarre.getReadOnlyProperty();
    }

    /// La position saisie, `{latitude, longitude}`, **seulement** si elle se lit et tient dans les limites
    /// du globe, vide sinon. Pensée pour la carte-outil : un champ vide ou refusé ne pose pas de marqueur.
    public Optional<double[]> coordonneesValides() {
        return position.coordonnees();
    }

    private void reinitialiserChamps() {
        code.set("");
        description.set("");
        position.texte().set("");
        retour.set(RetourOperation.AUCUN);
    }
}
