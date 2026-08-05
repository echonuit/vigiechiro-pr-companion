package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import fr.univ_amu.iut.analyse.model.AgregationAnalyse;
import fr.univ_amu.iut.analyse.model.ServiceAnalyse;
import fr.univ_amu.iut.validation.model.CarreEspeces;
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

/// `lister-carres` (#3269) : la **facette CLI de l'inventaire par carré** d'« Espèces & observations ».
///
/// Répond à « où », quand sa jumelle `lister-especes` répond à « quoi ». Mêmes critères, même
/// [AgregationAnalyse] que l'écran, mais une **richesse** par carré plutôt qu'un décompte par taxon.
///
/// La richesse dépend de ce qu'on a gardé, et c'est voulu : `--a-enjeu` donne la richesse **en espèces
/// prioritaires**, `--statut validee` celle qu'on peut défendre. C'est déjà ce que fait l'écran, où le
/// tableau se recalcule sous les filtres.
@Command(
        name = "lister-carres",
        description = "Liste l'inventaire par carré : richesse en espèces et détections, par carré prospecté.")
public final class ListerCarres implements Callable<Integer> {

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
    public ListerCarres(
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
        List<CarreEspeces> carres = AgregationAnalyse.parCarre(retenues);
        return sortie.rendre(
                ServiceAnalyse.tableCarres(carres),
                champsJson(carres),
                spec,
                "Inventaire exporté : " + carres.size() + " carré(s)");
    }

    private static Map<String, Object> champsJson(List<CarreEspeces> carres) {
        List<Object> lignes = new ArrayList<>();
        for (CarreEspeces carre : carres) {
            Map<String, Object> champs = new LinkedHashMap<>();
            champs.put("carre", carre.numeroCarre());
            champs.put("site", carre.nomSite());
            champs.put("richesse", carre.richesse());
            champs.put("detections", carre.nbObservations());
            champs.put("anneeMin", carre.anneeMin());
            champs.put("anneeMax", carre.anneeMax());
            lignes.add(champs);
        }
        Map<String, Object> racine = new LinkedHashMap<>();
        racine.put("carres", lignes);
        return racine;
    }
}
