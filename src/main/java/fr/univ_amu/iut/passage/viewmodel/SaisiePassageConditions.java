package fr.univ_amu.iut.passage.viewmodel;

import fr.univ_amu.iut.passage.model.CouvertureNuageuse;
import fr.univ_amu.iut.passage.model.Enregistreur;
import fr.univ_amu.iut.passage.model.MaterielMicro;
import fr.univ_amu.iut.passage.model.MeteoPassage;
import fr.univ_amu.iut.passage.model.MeteoReleve;
import fr.univ_amu.iut.passage.model.PositionMicro;
import fr.univ_amu.iut.passage.model.PropositionsEnregistreur;
import fr.univ_amu.iut.passage.model.ServiceConditionsPassage;
import fr.univ_amu.iut.passage.model.Vent;
import java.util.Optional;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/// Saisie des **conditions de dépôt** d'un passage (métadonnées demandées par VigieChiro), extraite de
/// [PassageViewModel] pour garder chaque ViewModel focalisé :
///
/// - le **relevé météo** (température début/fin, vent, couverture nuageuse ; #106 étendu), enregistré
///   d'un bloc via [ServiceConditionsPassage#definirMeteo] ;
/// - le **matériel du micro** (position sol/canopée, hauteur de fixation, type), enregistré d'un bloc via
///   [ServiceConditionsPassage#definirMateriel].
///
/// Chaque grandeur est **optionnelle** (vide = effacer). Une saisie non numérique publie un message
/// d'erreur **partagé** avec l'écran (la même ligne de message que [PassageViewModel]), sans rien
/// modifier. VM agnostique de l'IHM (`javafx.beans` uniquement).
public class SaisiePassageConditions {

    private final ServiceConditionsPassage service;

    /// Numéros à proposer quand l'utilisateur doit désigner l'enregistreur lui-même (#1828).
    private final PropositionsEnregistreur propositions;

    private final MessagesRattachement messages;
    private Long idPassage;

    private final StringProperty temperatureSaisie = new SimpleStringProperty(this, "temperatureSaisie", "");
    private final StringProperty temperatureFinSaisie = new SimpleStringProperty(this, "temperatureFinSaisie", "");
    private final ObjectProperty<Vent> ventSaisie = new SimpleObjectProperty<>(this, "ventSaisie");
    private final ObjectProperty<CouvertureNuageuse> couvertureNuageuseSaisie =
            new SimpleObjectProperty<>(this, "couvertureNuageuseSaisie");

    private final ObjectProperty<PositionMicro> positionSaisie = new SimpleObjectProperty<>(this, "positionSaisie");
    private final StringProperty hauteurSaisie = new SimpleStringProperty(this, "hauteurSaisie", "");
    private final StringProperty typeMicroSaisie = new SimpleStringProperty(this, "typeMicroSaisie", "");

    private final StringProperty enregistreurSaisie = new SimpleStringProperty(this, "enregistreurSaisie", "");

    /// Heures de la nuit (#1892), préoccupation à part : elles ne se saisissent que si rien ne les
    /// prouve. Voir [SaisieHorairesNuit].
    private final SaisieHorairesNuit horaires;
    private final ObservableList<String> enregistreursProposes = FXCollections.observableArrayList();

    SaisiePassageConditions(
            ServiceConditionsPassage service, PropositionsEnregistreur propositions, MessagesRattachement messages) {
        this.service = service;
        this.propositions = propositions;
        this.messages = messages;
        this.horaires = new SaisieHorairesNuit(service, messages);
    }

    /// Charge les conditions du passage `idPassage` : le relevé `meteo` (issu de la projection), le
    /// matériel (relu du service) et l'enregistreur. Tolérant : un relevé/matériel nul donne des champs
    /// vides.
    void charger(Long idPassage, MeteoReleve meteo, String idEnregistreur, String heureDebut, String heureFin) {
        this.idPassage = idPassage;
        appliquerMeteo(meteo);
        appliquerMateriel(service.materiel(idPassage));
        appliquerEnregistreur(idEnregistreur);
        horaires.charger(idPassage, heureDebut, heureFin);
    }

    /// Enregistre le **relevé météo** saisi (grandeur vide = effacer ; saisie non numérique = message
    /// d'erreur, sans modification), puis renormalise les champs. Renvoie `true` si l'enregistrement a
    /// réussi, `false` si une valeur était invalide (permet à un « Appliquer » groupé de rester ouvert).
    public boolean enregistrerMeteo() {
        try {
            MeteoReleve releve = new MeteoReleve(
                    MeteoPassage.lireSaisie(temperatureSaisie.get()),
                    MeteoPassage.lireSaisie(temperatureFinSaisie.get()),
                    ventSaisie.get(),
                    couvertureNuageuseSaisie.get());
            service.definirMeteo(idPassage, releve);
            appliquerMeteo(releve);
            messages.effacer();
            return true;
        } catch (NumberFormatException invalide) {
            messages.info("Valeur météo invalide : saisissez des nombres, ou laissez vide.");
            return false;
        }
    }

    /// Enregistre le **matériel du micro** saisi (grandeur vide = effacer ; hauteur non numérique =
    /// message d'erreur, sans modification), puis renormalise les champs. Renvoie `true` si
    /// l'enregistrement a réussi, `false` si la hauteur était invalide.
    public boolean enregistrerMateriel() {
        try {
            MaterielMicro materiel = new MaterielMicro(
                    idPassage,
                    positionSaisie.get(),
                    MeteoPassage.lireSaisie(hauteurSaisie.get()),
                    texteOuNull(typeMicroSaisie.get()));
            service.definirMateriel(materiel);
            appliquerMateriel(materiel);
            messages.effacer();
            return true;
        } catch (NumberFormatException invalide) {
            messages.info("Hauteur invalide : saisissez un nombre (m), ou laissez vide.");
            return false;
        }
    }

    /// Enregistre le **numéro de série de l'enregistreur** saisi ou choisi (#1828), quand ni l'import ni la
    /// plateforme ne l'ont fourni. Un champ **laissé vide** ne touche à rien : le schéma exige un
    /// enregistreur, la nuit garde donc son « inconnu » plutôt que de perdre sa clé étrangère.
    ///
    /// Saisir une **sentinelle** (« INCONNU ») est refusé : ce n'est pas un numéro, c'est un aveu, et le
    /// publier vers la plateforme fabriquerait une donnée.
    public boolean enregistrerEnregistreur() {
        String saisie = enregistreurSaisie.get();
        if (saisie != null && !saisie.isBlank() && Enregistreur.estInconnu(saisie)) {
            messages.info("« " + saisie.trim() + " » n'est pas un numéro de série : laissez vide si vous l'ignorez.");
            return false;
        }
        service.definirEnregistreur(idPassage, saisie);
        if (saisie != null) {
            enregistreurSaisie.set(saisie.trim());
        }
        messages.effacer();
        return true;
    }

    /// Saisie des heures de la nuit (#1892), déléguée à son propre ViewModel.
    public SaisieHorairesNuit horaires() {
        return horaires;
    }

    /// **Récupère** la météo de la nuit via le service (#547) : opération **réseau**, donc appelée par
    /// l'IHM **hors du fil JavaFX**. Renvoie le relevé (ou vide si indisponible). Ne touche à aucune
    /// propriété : le pré-remplissage se fait ensuite par [#appliquerMeteoRecuperee] (sur le fil JavaFX).
    public Optional<MeteoReleve> recupererMeteo() {
        return service.recupererMeteo(idPassage);
    }

    /// Pré-remplit les champs météo depuis un relevé **récupéré** (sur le fil JavaFX) : présent → les
    /// champs sont renseignés (l'utilisateur vérifie puis enregistre) ; absent → message d'aide, champs
    /// inchangés.
    public void appliquerMeteoRecuperee(Optional<MeteoReleve> releve) {
        if (releve.isPresent()) {
            appliquerMeteo(releve.get());
            messages.succes("Météo pré-remplie : vérifiez puis appliquez.");
        } else {
            messages.info("Météo indisponible (hors-ligne, pas de GPS ou données manquantes) : saisissez à la main.");
        }
    }

    private void appliquerMeteo(MeteoReleve releve) {
        MeteoReleve valeurs = releve == null ? MeteoReleve.VIDE : releve;
        temperatureSaisie.set(texteOuVide(valeurs.temperatureDebutNuit()));
        temperatureFinSaisie.set(texteOuVide(valeurs.temperatureFinNuit()));
        ventSaisie.set(valeurs.vent());
        couvertureNuageuseSaisie.set(valeurs.couvertureNuageuse());
    }

    /// Reflète l'enregistreur courant et rafraîchit les propositions. Une **sentinelle** s'affiche comme un
    /// champ **vide** : c'est un aveu d'ignorance, pas une valeur à recopier, et un champ vide invite à la
    /// saisie.
    private void appliquerEnregistreur(String idEnregistreur) {
        enregistreurSaisie.set(Enregistreur.estInconnu(idEnregistreur) ? "" : idEnregistreur);
        enregistreursProposes.setAll(propositions.pour(idPassage));
    }

    private void appliquerMateriel(MaterielMicro materiel) {
        positionSaisie.set(materiel == null ? null : materiel.positionMicro());
        hauteurSaisie.set(materiel == null ? "" : texteOuVide(materiel.hauteurMetres()));
        typeMicroSaisie.set(materiel == null || materiel.typeMicro() == null ? "" : materiel.typeMicro());
    }

    private static String texteOuVide(Double valeur) {
        return valeur == null ? "" : valeur.toString();
    }

    private static String texteOuNull(String saisie) {
        return saisie == null || saisie.isBlank() ? null : saisie.trim();
    }

    public StringProperty temperatureSaisieProperty() {
        return temperatureSaisie;
    }

    public StringProperty temperatureFinSaisieProperty() {
        return temperatureFinSaisie;
    }

    public ObjectProperty<Vent> ventSaisieProperty() {
        return ventSaisie;
    }

    public ObjectProperty<CouvertureNuageuse> couvertureNuageuseSaisieProperty() {
        return couvertureNuageuseSaisie;
    }

    public ObjectProperty<PositionMicro> positionSaisieProperty() {
        return positionSaisie;
    }

    public StringProperty hauteurSaisieProperty() {
        return hauteurSaisie;
    }

    /// N° de série de l'enregistreur, saisi ou choisi parmi [#enregistreursProposes] (#1828). Vide quand la
    /// nuit ne sait pas quel appareil l'a produite.
    public StringProperty enregistreurSaisieProperty() {
        return enregistreurSaisie;
    }

    /// Numéros à proposer pour cette nuit : ceux lus dans les **noms de fichiers** de la nuit d'abord
    /// (journal, originaux), puis les enregistreurs déjà connus du poste. Peut être vide.
    public ObservableList<String> enregistreursProposes() {
        return enregistreursProposes;
    }

    public StringProperty typeMicroSaisieProperty() {
        return typeMicroSaisie;
    }
}
