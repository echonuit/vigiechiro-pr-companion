package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/// **Rattrapage des métadonnées** d'un lot de nuits (#1861) : rejoue, sur toutes les nuits liées à une
/// participation, les deux gestes que seule la modale offrait jusqu'ici - récupérer depuis Vigie-Chiro,
/// envoyer vers Vigie-Chiro.
///
/// Ce n'est pas une commodité de confort. Trois correctifs de ce chantier ne réparent que la nuit sur
/// laquelle on repasse :
///  - une nuit rapatriée avant #1814 garde « PR INCONNU » (la récupération adopte enfin le n° distant) ;
///  - une participation déposée avant #1828 porte la sentinelle « INCONNU », et avant #1844 son n° de
///    série sous une clé que le formulaire web ne lit pas (l'envoi réécrit les bonnes clés) ;
///  - une nuit dont les heures ont dérivé (#1860) se réaligne sur ses enregistrements à l'envoi (#1878).
///
/// Dans les trois cas la réparation existait déjà, mais **nuit par nuit, à la main**. Sur une saison, ce
/// n'est pas tenable : le geste unitaire livré n'atteint jamais les nuits abîmées qu'il était censé
/// réparer. C'est le lot qui rend le correctif effectif.
///
/// **Best-effort par nuit**, comme la reconstruction groupée (#1708) : une nuit qui échoue est *ignorée
/// en le disant*, le lot continue. Un incident isolé - participation supprimée côté plateforme, point
/// d'écoute introuvable - ne doit pas priver les autres nuits de leur réparation.
///
/// La boucle vit **ici** et non dans la commande : la politique best-effort est du métier, et se teste
/// sans picocli. La surface ne garde que son rendu, via [IssuePassage].
public final class RattrapageMetadonnees {

    private final SynchronisationParticipation synchronisation;
    private final LienVigieChiroDao liens;

    public RattrapageMetadonnees(SynchronisationParticipation synchronisation, LienVigieChiroDao liens) {
        this.synchronisation = Objects.requireNonNull(synchronisation, "synchronisation");
        this.liens = Objects.requireNonNull(liens, "liens");
    }

    /// Identifiants des passages **liés à une participation**, en ordre croissant : le périmètre exact du
    /// rattrapage.
    ///
    /// Une nuit sans lien est hors sujet - il n'y a rien à récupérer ni où envoyer. Elle est **écartée
    /// d'emblée** plutôt que traitée puis comptée en échec : un bilan qui annonce trente ignorées dont
    /// vingt-huit « pas encore déposées » n'apprend rien.
    public List<Long> passagesLies() {
        List<Long> passages = new ArrayList<>();
        for (String refLocale : liens.tous(LienVigieChiro.ENTITE_PASSAGE).keySet()) {
            enIdentifiant(refLocale).ifPresent(passages::add);
        }
        return passages.stream().sorted().toList();
    }

    /// Rejoue les gestes demandés sur chacune des nuits de `passages`, dans l'ordre reçu.
    ///
    /// Quand les deux sont demandés, la **récupération précède l'envoi** : on part de l'état de la
    /// plateforme avant de le réécrire, conformément à l'[ADR 0020] (ne rien effacer). L'inverse
    /// écraserait avec un état local possiblement plus pauvre ce que le web portait déjà.
    ///
    /// @param passages nuits à traiter (typiquement [#passagesLies])
    /// @param recuperer rapatrier météo, micro et enregistreur depuis la participation
    /// @param envoyer réécrire les métadonnées locales sur la participation
    /// @param issueParPassage émis **après** chaque nuit : de quoi rendre compte nuit par nuit
    /// @return le bilan du lot
    public BilanRattrapage rattraper(
            List<Long> passages, boolean recuperer, boolean envoyer, Consumer<IssuePassage> issueParPassage) {
        Objects.requireNonNull(passages, "passages");
        Objects.requireNonNull(issueParPassage, "issueParPassage");
        int traites = 0;
        int ignores = 0;
        int realignes = 0;
        for (Long idPassage : passages) {
            IssuePassage issue = traiter(idPassage, recuperer, envoyer);
            if (issue.estTraitee()) {
                traites++;
                realignes += issue.realignement().isPresent() ? 1 : 0;
            } else {
                ignores++;
            }
            issueParPassage.accept(issue);
        }
        return new BilanRattrapage(traites, ignores, realignes);
    }

    /// Traite **une** nuit, sans jamais laisser remonter son échec : c'est ce qui rend le lot best-effort.
    ///
    /// Deux formes d'échec sont ramenées au même compte rendu, parce qu'elles se valent pour qui lit le
    /// bilan : la règle métier qui empêche le geste ([RegleMetierException]), et l'écriture que la
    /// plateforme a refusée. Cette seconde ne lève rien - elle rend un [ResultatEcriture] en échec, qu'il
    /// faut donc **regarder** ([ADR 0008], aucun échec silencieux). Une nuit comptée « traitée » alors que
    /// la plateforme a rejeté l'écriture serait exactement le mensonge que cet ADR interdit.
    private IssuePassage traiter(Long idPassage, boolean recuperer, boolean envoyer) {
        try {
            if (recuperer) {
                synchronisation.tirerDepuis(idPassage);
            }
            if (!envoyer) {
                return new IssuePassage.Traite(idPassage, recuperer, false, Optional.empty());
            }
            EnvoiParticipation envoi = synchronisation.pousserVers(idPassage);
            if (!envoi.ecriture().estReussie()) {
                return new IssuePassage.Ignore(idPassage, envoi.ecriture().echec());
            }
            return new IssuePassage.Traite(idPassage, recuperer, true, envoi.realignement());
        } catch (RegleMetierException echecNuit) {
            return new IssuePassage.Ignore(idPassage, echecNuit.getMessage());
        }
    }

    /// Identifiant numérique d'une clé locale de correspondance, ou vide si la table en porte une qui
    /// n'en est pas une. Un lien corrompu ne doit pas faire tomber tout le rattrapage.
    private static Optional<Long> enIdentifiant(String refLocale) {
        try {
            return Optional.of(Long.valueOf(refLocale));
        } catch (NumberFormatException pasUnIdentifiant) {
            return Optional.empty();
        }
    }

    /// Bilan d'un rattrapage : nuits **traitées**, nuits **ignorées** (best-effort), et parmi les traitées
    /// celles dont les heures ont été **réalignées** sur leurs enregistrements (#1878).
    ///
    /// Le décompte des réalignements est séparé parce qu'il répond à une autre question que « ça a
    /// marché » : il dit **combien de nuits étaient fausses**, c'est-à-dire l'ampleur de la dérive que le
    /// lot vient de corriger.
    public record BilanRattrapage(int traites, int ignores, int realignes) {}

    /// Issue d'**une** nuit du lot : traitée (avec ce qui a été fait) ou ignorée (avec la cause). Chaque
    /// surface en tire son rendu sans que le métier ne connaisse ni CLI ni IHM.
    public sealed interface IssuePassage permits IssuePassage.Traite, IssuePassage.Ignore {

        Long passage();

        /// `true` si le geste a abouti sur cette nuit. Chaque variante **répond pour elle-même** plutôt
        /// que de laisser l'appelant tester son type : le bilan n'a pas à connaître la liste des issues.
        boolean estTraitee();

        /// Heures corrigées d'après les enregistrements (#1878), vide si rien n'a bougé - et vide aussi,
        /// naturellement, pour une nuit ignorée.
        Optional<EnvoiParticipation.Realignement> realignement();

        record Traite(
                Long passage, boolean recupere, boolean envoye, Optional<EnvoiParticipation.Realignement> realignement)
                implements IssuePassage {

            @Override
            public boolean estTraitee() {
                return true;
            }
        }

        record Ignore(Long passage, String cause) implements IssuePassage {

            @Override
            public boolean estTraitee() {
                return false;
            }

            @Override
            public Optional<EnvoiParticipation.Realignement> realignement() {
                return Optional.empty();
            }
        }
    }
}
