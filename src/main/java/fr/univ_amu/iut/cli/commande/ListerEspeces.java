package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import fr.univ_amu.iut.analyse.model.AgregationAnalyse;
import fr.univ_amu.iut.analyse.model.ServiceAnalyse;
import fr.univ_amu.iut.cli.LectureSeule;
import fr.univ_amu.iut.validation.model.EspeceAgregee;
import fr.univ_amu.iut.validation.model.EspecesPrioritaires;
import fr.univ_amu.iut.validation.model.ObservationAnalyse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/// `lister-especes` (#3269) : la **facette CLI de l'inventaire par espèce** d'« Espèces & observations ».
///
/// Même [AgregationAnalyse#parEspece] que l'écran, mêmes colonnes que son export : le tableau ne peut pas
/// dire une chose ici et une autre là. Seule la façon de désigner ce qu'on garde change - l'écran fait
/// cocher dans une liste, la ligne de commande reçoit un fragment tapé.
///
/// Sa jumelle `lister-carres` répond à l'autre question du même écran : « quelles espèces », et « où ».
/// Deux commandes plutôt qu'une à `--regrouper`, parce que leurs colonnes n'ont rien en commun : un
/// script qui lit l'une n'a pas à se demander quelles colonnes il va recevoir.
@Command(
        name = "lister-especes",
        description = "Liste l'inventaire par espèce : détections, passages, carrés et points, par taxon.")
public final class ListerEspeces implements Callable<Integer>, LectureSeule {

    @Mixin
    private InventaireFiltre filtre;

    @Mixin
    private SortieInventaire sortie;

    @Spec
    private CommandSpec spec;

    private final ServiceAnalyse service;

    /// Identifiant de l'utilisateur courant. En `Provider` : la commande est instanciée par picocli
    /// **avant** la migration du schéma, et le résoudre au constructeur ouvrirait la base trop tôt.
    private final Provider<String> utilisateur;

    /// Référentiel des espèces à enjeu, pour `--a-enjeu`. En `Provider` pour la même raison.
    private final Provider<EspecesPrioritaires> especesPrioritaires;

    @Inject
    public ListerEspeces(
            ServiceAnalyse service,
            @Named("idUtilisateurCourant") Provider<String> utilisateur,
            Provider<EspecesPrioritaires> especesPrioritaires) {
        this.service = Objects.requireNonNull(service, "service");
        this.utilisateur = Objects.requireNonNull(utilisateur, "utilisateur");
        this.especesPrioritaires = Objects.requireNonNull(especesPrioritaires, "especesPrioritaires");
    }

    @Override
    public Integer call() throws IOException {
        if (!sortie.formatReconnu(spec)) {
            return ExitCode.USAGE;
        }
        List<ObservationAnalyse> retenues = filtre.appliquer(
                service.observationsAnalyse(utilisateur.get()),
                service.nuitsOpportunistes(),
                especesPrioritaires.get(),
                spec.commandLine().getErr()::println);
        List<EspeceAgregee> especes = AgregationAnalyse.parEspece(retenues);
        return sortie.rendre(
                ServiceAnalyse.tableEspeces(especes),
                champsJson(especes),
                spec,
                "Inventaire exporté : " + especes.size() + " espèce(s)");
    }

    private static Map<String, Object> champsJson(List<EspeceAgregee> especes) {
        List<Object> lignes = new ArrayList<>();
        for (EspeceAgregee espece : especes) {
            Map<String, Object> champs = new LinkedHashMap<>();
            champs.put("code", espece.code());
            champs.put("nomLatin", espece.nomLatin());
            champs.put("nomVernaculaire", espece.nomVernaculaireFr());
            champs.put("groupe", espece.groupe());
            champs.put("detections", espece.nbObservations());
            champs.put("passages", espece.nbPassages());
            champs.put("carres", espece.nbCarres());
            champs.put("points", espece.nbPoints());
            champs.put("anneeMin", espece.anneeMin());
            champs.put("anneeMax", espece.anneeMax());
            lignes.add(champs);
        }
        Map<String, Object> racine = new LinkedHashMap<>();
        racine.put("especes", lignes);
        return racine;
    }
}
