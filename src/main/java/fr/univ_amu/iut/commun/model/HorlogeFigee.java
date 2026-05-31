package fr.univ_amu.iut.commun.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Implémentation déterministe de {@link Horloge} : le temps est <b>figé</b> sur un instant fourni à
 * la construction. C'est l'équivalent maison de {@code java.time.Clock.fixed(...)}.
 *
 * <p>Réservée aux tests : on injecte cette horloge au service pour que toute écriture de date soit
 * prévisible (ex. asserter que la date de création d'un site vaut bien {@code "2026-05-31"}). Elle
 * vit en {@code commun.model} (et non dans {@code src/test}) pour être réutilisable par les tests
 * de toutes les features.
 *
 * <pre>{@code
 * Horloge horloge = new HorlogeFigee(LocalDate.of(2026, 5, 31));
 * ServiceSites service = new ServiceSites(siteDao, pointDao, passageDao, horloge);
 * }</pre>
 */
public final class HorlogeFigee implements Horloge {

  private final LocalDateTime instant;

  /** Fige l'horloge sur {@code jour} à minuit. */
  public HorlogeFigee(LocalDate jour) {
    this(Objects.requireNonNull(jour, "jour").atStartOfDay());
  }

  /** Fige l'horloge sur un instant précis (date + heure). */
  public HorlogeFigee(LocalDateTime instant) {
    this.instant = Objects.requireNonNull(instant, "instant");
  }

  @Override
  public LocalDate aujourdhui() {
    return instant.toLocalDate();
  }

  @Override
  public LocalDateTime maintenant() {
    return instant;
  }
}
