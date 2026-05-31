package fr.univ_amu.iut.commun.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Abstraction du temps : permet aux services de lire « la date du jour » sans dépendre directement
 * de {@link LocalDate#now()}.
 *
 * <p>Pourquoi cette indirection ? Les règles métier de dates (R3 : fenêtres de passage, R4 :
 * intervalle entre deux passages) et l'horodatage des entités (date de création d'un site, date de
 * dépôt d'un lot) dépendent de « maintenant ». Si un service appelait {@code LocalDate.now()}
 * directement, ses tests seraient <b>non déterministes</b> (le résultat changerait chaque jour). En
 * injectant une {@code Horloge}, le test fournit une {@link HorlogeFigee} sur une date connue et
 * peut alors asserter exactement la date écrite en base.
 *
 * <p>En production, le socle ({@code CommunModule}) binde {@link #systeme()} (l'horloge réelle). En
 * test, on construit directement une {@link HorlogeFigee} et on la passe au constructeur du
 * service.
 */
public interface Horloge {

  /** Date locale courante (ISO {@code AAAA-MM-JJ} via {@link LocalDate#toString()}). */
  LocalDate aujourdhui();

  /** Instant local courant (utile pour un horodatage complet, ex. date/heure de dépôt). */
  LocalDateTime maintenant();

  /** Horloge réelle, adossée à l'horloge système. C'est l'implémentation de production. */
  static Horloge systeme() {
    return new HorlogeSysteme();
  }

  /** Horloge figée sur une date donnée (minuit) : réservée aux tests déterministes. */
  static Horloge figeeAu(LocalDate jour) {
    return new HorlogeFigee(jour);
  }
}
