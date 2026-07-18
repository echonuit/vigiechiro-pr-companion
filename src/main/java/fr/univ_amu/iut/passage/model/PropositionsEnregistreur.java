package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.passage.model.dao.EnregistrementOriginalDao;
import fr.univ_amu.iut.passage.model.dao.EnregistreurDao;
import fr.univ_amu.iut.passage.model.dao.JournalDuCapteurDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// **Numéros de série à proposer** quand l'utilisateur doit désigner lui-même l'enregistreur d'une nuit
/// (#1828). Deux gisements, du plus spécifique au plus général :
///
/// 1. **les noms de fichiers de cette nuit-là** : le journal du capteur (`LogPR<serie>.txt`) puis les
///    enregistrements originaux (`…PaRecPR<serie>_…`). C'est la logique même de l'import, qui lit
///    l'identité de l'appareil dans son journal : la proposition dit donc ce que l'import aurait déduit ;
/// 2. **les enregistreurs déjà connus** de ce poste (table `recorder`) : un observateur en possède un ou
///    deux, et celui d'une autre nuit est le meilleur candidat suivant.
///
/// Les sentinelles ([Enregistreur#estInconnu]) sont écartées : proposer « INCONNU » n'aiderait personne.
/// **Rien n'est lu sur le disque** - seuls les *noms* déjà enregistrés en base sont analysés. La
/// proposition survit donc à une purge des fichiers, et fonctionne même pour un import en mode dégradé,
/// où le journal manquait mais où les WAV portaient le numéro.
public final class PropositionsEnregistreur {

    /// `LogPR1925492.txt` → `1925492`.
    private static final Pattern SERIE_JOURNAL = Pattern.compile("LogPR(\\d+)", Pattern.CASE_INSENSITIVE);

    /// `…-PaRecPR1925492_20260703_220529_000.wav` → `1925492` (suffixe enregistreur R7).
    private static final Pattern SERIE_FICHIER = Pattern.compile("PaRecPR(\\d+)", Pattern.CASE_INSENSITIVE);

    private final EnregistreurDao enregistreurDao;
    private final SessionDao sessionDao;
    private final JournalDuCapteurDao journauxDao;
    private final EnregistrementOriginalDao originauxDao;

    public PropositionsEnregistreur(
            EnregistreurDao enregistreurDao,
            SessionDao sessionDao,
            JournalDuCapteurDao journauxDao,
            EnregistrementOriginalDao originauxDao) {
        this.enregistreurDao = Objects.requireNonNull(enregistreurDao, "enregistreurDao");
        this.sessionDao = Objects.requireNonNull(sessionDao, "sessionDao");
        this.journauxDao = Objects.requireNonNull(journauxDao, "journauxDao");
        this.originauxDao = Objects.requireNonNull(originauxDao, "originauxDao");
    }

    /// Les numéros à proposer pour ce passage, **dans l'ordre d'intérêt** et sans doublon : ceux lus dans
    /// les noms de fichiers de la nuit d'abord, puis les autres déjà connus du poste. Liste vide si on ne
    /// sait rien proposer (l'utilisateur saisira le numéro à la main).
    public List<String> pour(Long idPassage) {
        Set<String> propositions = new LinkedHashSet<>(depuisLesFichiers(idPassage));
        enregistreurDao.findAll().stream()
                .map(Enregistreur::numeroSerie)
                .filter(serie -> !Enregistreur.estInconnu(serie))
                .forEach(propositions::add);
        return List.copyOf(propositions);
    }

    /// Les numéros lisibles dans les **noms de fichiers** de la nuit : le journal d'abord (c'est la source
    /// d'identité de l'import), les originaux ensuite.
    private Set<String> depuisLesFichiers(Long idPassage) {
        Set<String> series = new LinkedHashSet<>();
        Optional<Long> idSession = sessionDao.trouverParPassage(idPassage).map(SessionDEnregistrement::id);
        if (idSession.isEmpty()) {
            return series;
        }
        journauxDao
                .trouverParSession(idSession.get())
                .map(JournalDuCapteur::cheminFichier)
                .flatMap(chemin -> extraire(SERIE_JOURNAL, chemin))
                .ifPresent(series::add);
        originauxDao.findBySession(idSession.get()).stream()
                .map(EnregistrementOriginal::nomFichier)
                .map(nom -> extraire(SERIE_FICHIER, nom))
                .flatMap(Optional::stream)
                .forEach(series::add);
        return series;
    }

    private static Optional<String> extraire(Pattern motif, String texte) {
        if (texte == null) {
            return Optional.empty();
        }
        Matcher trouve = motif.matcher(texte);
        return trouve.find() ? Optional.of(trouve.group(1)) : Optional.empty();
    }
}
