-- V07 - Correction d'unité de la fréquence médiane Tadarida.
--
-- La colonne était nommée `median_freq_hz`, mais la valeur fournie par Tadarida (colonne
-- `frequence_mediane` du CSV) est en **kHz**, pas en Hz : les fréquences de pic des chiroptères
-- se lisent en dizaines de kHz (Pippip ~50 kHz, rhinolophes >100 kHz), et le maximum observé
-- (~188) reste sous le Nyquist des bruts 384 kHz (192 kHz). Lue en Hz, la valeur n'avait aucun
-- sens (8-188 Hz = infrasons). On renomme donc la colonne pour refléter l'unité réelle ; la
-- valeur stockée est inchangée (aucune conversion : elle était déjà en kHz).
ALTER TABLE observation RENAME COLUMN median_freq_hz TO median_freq_khz;
