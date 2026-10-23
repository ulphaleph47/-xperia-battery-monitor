#!/usr/bin/env python3
"""Generează un istoric de test (14 zile) pentru verificarea aplicației în emulator.

Folosire: make_samples.py <ora_acum_în_secunde>
Ciclul zilnic imită un telefon real: descărcare ~16 ore, încărcare ~3,6 ore, apoi conectat la 100%.
Momentul "acum" cade în partea de descărcare, la aproximativ 63%.
"""
import random
import sys

now = int(sys.argv[1])
random.seed(7)

STEP = 15 * 60
DAYS = 14
RATE = 4.9            # puncte procentuale pe oră la descărcare
DISCHARGE_H = 16.0
START_LEVEL = 100.0
L0 = START_LEVEL - RATE * DISCHARGE_H   # nivelul la începutul încărcării
CYCLE_ORIGIN = now - int(7.5 * 3600)    # "acum" = ora 7,5 a ciclului

for ts in range(now - DAYS * 24 * 3600, now + 1, STEP):
    h = ((ts - CYCLE_ORIGIN) / 3600.0) % 24.0
    days_ago = (now - ts) / 86400.0
    cap_mah = 4860.0 - 0.6 * (DAYS - days_ago)   # uzură foarte lentă

    if h < DISCHARGE_H:
        level = START_LEVEL - RATE * h
        status, plugged = 3, 0
        current = -random.randint(450_000, 900_000)
        temp = random.uniform(28.5, 33.5)
    elif h < DISCHARGE_H + 3.58:
        t = h - DISCHARGE_H
        if t < 1.767:
            level = L0 + 33.0 * t
            current = random.randint(2_400_000, 2_900_000)
        else:
            level = 80.0 + 11.0 * (t - 1.767)
            current = random.randint(700_000, 1_200_000)
        status, plugged = 2, 1
        temp = random.uniform(34.0, 37.5)
    else:
        level = 100.0
        status, plugged = 5, 1
        current = 0
        temp = random.uniform(30.0, 33.0)

    level = max(1, min(100, int(round(level))))
    charge_uah = int(level / 100.0 * cap_mah * 1000 + random.randint(-15_000, 15_000))
    mv = int(3600 + 6.3 * level + random.randint(-10, 10))
    print(f"{ts * 1000},{level},{temp:.1f},{mv},{current},{charge_uah},{status},{plugged}")
