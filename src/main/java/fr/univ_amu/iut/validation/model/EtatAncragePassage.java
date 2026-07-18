package fr.univ_amu.iut.validation.model;

import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.validation.model.dao.ObservationDao;
import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;
import java.util.List;
import java.util.Objects;

/// Questions d'**ancrage plateforme** d'un passage : ses observations portent-elles leur
/// `idDonneeVigieChiro` (la cible du `PATCH /donnees/{id}/observations/{index}`, contrat #1203) ? Un
/// passage **reconstruit par CSV** (#1565) n'a aucun ancrage tant qu'il n'a pas été **réactivé** (#1571 :
/// l'audio revenu permet de le racquérir). Extrait de [ServiceValidation] (cohésion, plafond GodClass) :
/// ces deux questions forment une unité, distincte de la revue et de l'import.
public final class EtatAncragePassage {

    private final ResultatsIdentificationDao resultatsDao;
    private final ObservationDao observationDao;

    /// Liens plateforme : sert à savoir si la nuit est **rattachée** à une participation, seule
    /// condition pour que l'ancrage absent puisse encore être acquis (#1838). Lecture locale.
    private final LienVigieChiroDao liens;

    public EtatAncragePassage(
            ResultatsIdentificationDao resultatsDao, ObservationDao observationDao, LienVigieChiroDao liens) {
        this.resultatsDao = Objects.requireNonNull(resultatsDao, "resultatsDao");
        this.observationDao = Objects.requireNonNull(observationDao, "observationDao");
        this.liens = Objects.requireNonNull(liens, "liens");
    }

    /// **Au moins une** observation du passage est sans ancrage (`idDonneeVigieChiro == null`) : de quoi
    /// décider la **ré-acquisition** de l'ancrage à la réactivation (#1571). `false` si le passage n'a pas
    /// d'observations, ou si toutes sont déjà ancrées.
    public boolean manquant(Long idPassage) {
        return observations(idPassage).stream().anyMatch(observation -> observation.idDonneeVigieChiro() == null);
    }

    /// **Aucune** observation du passage n'est ancrée (toutes `idDonneeVigieChiro == null`) : l'état d'un
    /// passage reconstruit par CSV non réactivé, où **rien** n'est publiable. L'IHM grise alors
    /// proactivement « publier les corrections » (#1596). `false` dès qu'au moins une est ancrée (une
    /// publication partielle reste possible, les non ancrées étant écartées à l'envoi), ou si le passage
    /// n'a pas d'observations.
    public boolean aucun(Long idPassage) {
        List<Observation> observations = observations(idPassage);
        return !observations.isEmpty()
                && observations.stream().allMatch(observation -> observation.idDonneeVigieChiro() == null);
    }

    /// **Rien n'est publiable, et rien ne le deviendra** (#1838) : aucune observation ancrée **et** nuit
    /// non rattachée à une participation, donc sans rien à quoi s'ancrer. C'est ce que l'IHM grise.
    ///
    /// Le grisage portait auparavant sur le seul [#aucun] : une nuit importée par CSV (#1565) restait
    /// donc bloquée jusqu'à sa réactivation. Depuis que la publication acquiert elle-même l'ancrage qui
    /// lui manque, ce n'est plus un cul-de-sac dès lors que la nuit est rattachée - griser sur [#aucun]
    /// interdirait précisément le cas que #1838 vient de rendre possible.
    ///
    /// (Quand **aucune** observation n'est ancrée, l'ancrage manque nécessairement : le rattachement est
    /// donc la seule inconnue restante.)
    public boolean publicationImpossible(Long idPassage) {
        return aucun(idPassage) && !rattache(idPassage);
    }

    /// La nuit est-elle rattachée à une participation VigieChiro ? Simple présence d'un lien en base.
    private boolean rattache(Long idPassage) {
        return liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, String.valueOf(idPassage))
                .isPresent();
    }

    /// Les observations du jeu de résultats du passage, ou une liste vide s'il n'a pas encore d'import.
    private List<Observation> observations(Long idPassage) {
        Objects.requireNonNull(idPassage, "idPassage");
        return resultatsDao
                .findByPassage(idPassage)
                .map(resultats -> observationDao.findByResults(resultats.id()))
                .orElseGet(List::of);
    }
}
