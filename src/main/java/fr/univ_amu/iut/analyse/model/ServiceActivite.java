package fr.univ_amu.iut.analyse.model;

import fr.univ_amu.iut.commun.model.PlageNuit;
import fr.univ_amu.iut.passage.model.FenetreObserveeNuit;
import fr.univ_amu.iut.passage.model.NuitsOpportunistes;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.PlageNuitPassage;
import fr.univ_amu.iut.validation.model.dao.ProjectionsAudioDao;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/// Service de la **courbe d'activité** (#2352, lot 2 du chantier #2348). Rassemble ce qu'il faut pour
/// tracer l'activité d'un **passage** ou de **tous les passages** de l'utilisateur : les **contacts datés**
/// des observations et la **fenêtre nocturne** (coucher → lever) à matérialiser sous la courbe.
///
/// Il ne fait que **lire et projeter** : l'agrégation en tranches ([AgregationActivite]) et la réactivité
/// (tranche, sélection d'espèces) restent au ViewModel, sur le modèle de la feature `analyse`
/// ([ServiceAnalyse] lit, le ViewModel agrège). Les deux sources sont déjà publiées par la feature
/// `validation` : [ProjectionsAudioDao] pour les observations (avec leur `heureCapture`), [PlageNuitPassage]
/// pour la nuit réelle au point d'écoute.
public class ServiceActivite {

    /// Pseudo-taxons Tadarida qui ne sont pas des espèces : `noise` (bruit) et `piaf` (oiseau générique).
    /// Écartés de la courbe comme [...validation.model.dao.ProjectionsAnalyseDao] les écarte de
    /// l'inventaire (`NOT IN ('noise', 'piaf')`).
    private static final Set<String> PSEUDO_TAXONS = Set.of("noise", "piaf");

    private final ProjectionsAudioDao projections;
    private final PlageNuitPassage plageNuitPassage;
    private final FenetreObserveeNuit fenetreObservee;
    private final NuitsOpportunistes nuitsOpportunistes;

    public ServiceActivite(
            ProjectionsAudioDao projections,
            PlageNuitPassage plageNuitPassage,
            FenetreObserveeNuit fenetreObservee,
            NuitsOpportunistes nuitsOpportunistes) {
        this.projections = projections;
        this.plageNuitPassage = plageNuitPassage;
        this.fenetreObservee = fenetreObservee;
        this.nuitsOpportunistes = nuitsOpportunistes;
    }

    /// Les contacts datés d'un **passage**, prêts pour l'agrégation : chaque observation devient un
    /// [ContactHoraire] (taxon retenu, nom, groupe, heure de capture, contexte carré/point/passage), les
    /// pseudo-taxons `noise`/`piaf` étant écartés. Les séquences non identifiées (sans taxon) et non
    /// horodatées **restent** dans la liste : c'est [AgregationActivite] qui décide de leur sort.
    public List<ContactHoraire> contactsDuPassage(long idPassage) {
        return versContacts(projections.lignesAudioDuPassage(idPassage));
    }

    /// **Tous** les contacts datés de l'utilisateur (tous ses passages), pour la vue Activité filtrable sur
    /// l'ensemble des nuits : le sous-ensemble (carré, point, passage, espèce) se fait ensuite côté client,
    /// via le socle `Filtres`. Même projection et mêmes exclusions que [#contactsDuPassage].
    public List<ContactHoraire> contactsDeLUtilisateur(String idUtilisateur) {
        return versContacts(projections.lignesAudioDeLUtilisateur(idUtilisateur));
    }

    private static List<ContactHoraire> versContacts(List<LigneObservationAudio> lignes) {
        return lignes.stream()
                .filter(ligne -> !estPseudoTaxon(taxonRetenu(ligne)))
                .map(ServiceActivite::versContact)
                .toList();
    }

    /// La fenêtre nocturne réelle (heures pleines coucher → lever) du passage, ou **vide** si elle n'est
    /// pas calculable (passage sans date, sans coordonnées de point, ou nuit polaire). La vue trace alors
    /// la courbe sans aplat nocturne, sans être empêchée.
    public Optional<PlageNuit> plageNuit(long idPassage) {
        return plageNuitPassage.pour(idPassage);
    }

    /// La fenêtre **réellement enregistrée** de la nuit (premier et dernier enregistrement effectifs,
    /// [FenetreObserveeNuit]), ou **vide** si la nuit n'a laissé aucune trace exploitable.
    ///
    /// Sert à savoir **où l'on écoutait** : c'est la seule plage sur laquelle une tranche sans contact
    /// vaut légitimement zéro. En dehors, l'absence de contact ne dit rien (le capteur était peut-être
    /// éteint), et la courbe s'abstient plutôt que d'affirmer un silence observé.
    public Optional<FenetreObserveeNuit.Bornes> fenetreEnregistree(long idPassage) {
        return fenetreObservee.pour(idPassage);
    }

    /// Les passages marqués **opportunistes** (#2525), pour la dimension de filtre « Nature de la nuit »
    /// (#2614). Lecture groupée et rare : le ViewModel en garde un instantané le temps d'un chargement,
    /// plutôt qu'une requête par contact filtré.
    public Set<Long> nuitsOpportunistes() {
        return nuitsOpportunistes.identifiants();
    }

    private static ContactHoraire versContact(LigneObservationAudio ligne) {
        return new ContactHoraire(
                taxonRetenu(ligne),
                ligne.nomEspece(),
                ligne.groupe(),
                ligne.heureCapture(),
                ligne.commune(),
                ligne.numeroCarre(),
                ligne.codePoint(),
                ligne.idPassage());
    }

    /// Taxon **retenu** d'une ligne : la correction de l'observateur si elle existe, sinon la proposition
    /// Tadarida (`COALESCE(taxon_observer, taxon_tadarida)`), comme partout ailleurs. `null` pour une
    /// séquence non identifiée.
    private static String taxonRetenu(LigneObservationAudio ligne) {
        return ligne.taxonObservateur() != null ? ligne.taxonObservateur() : ligne.taxonTadarida();
    }

    /// Vrai pour `noise`/`piaf`, faux pour tout autre code **et pour `null`** (une séquence non identifiée
    /// n'est pas un pseudo-taxon : c'est [AgregationActivite] qui l'écarte, pas cette projection).
    private static boolean estPseudoTaxon(String taxon) {
        return taxon != null && PSEUDO_TAXONS.contains(taxon);
    }
}
