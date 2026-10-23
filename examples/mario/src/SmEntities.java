/**
 * Entity data: the hero, walking enemies / shells, and transient items.
 *
 * @author 张晓达
 */
class SmPlayer extends SmBody {
    // physics constants (pixels per frame; 60 Hz fixed step)
    static double WALK_ACC = 0.0371;
    static double RUN_ACC = 0.0557;
    static double FRICTION = 0.0508;
    static double SKID_DECEL = 0.1016;
    static double MAX_WALK = 1.5625;
    static double MAX_RUN = 2.5625;
    static double MAX_FALL = 4.0;

    int big;
    int fire;
    int star;
    int facing = 1;
    int crouching;
    int skidding;
    int jumping;
    double gravityHold = 0.125;
    double gravityFall = 0.4375;
    int invincible;
    int walkTimer;
    int walkFrame;
    int stompCombo;

    /** 0 alive, 1 growing, 2 shrinking, 3 dying, 4 flag slide, 5 walking to castle, 6 finished. */
    int state;
    int stateTimer;

    SmPlayer() {
        w = 12;
        h = 16;
    }

    void setBig(int b) {
        if (b == big) {
            return;
        }
        double feet = y + h;
        big = b;
        if (big != 0) {
            h = 32;
        } else {
            h = 16;
        }
        y = feet - h;
    }

    /** One physics step for an alive hero. */
    void update(SmLevel lv, int left, int right, int run, int jumpHeld, int jumpPressed, int down) {
        int dir = 0;
        if (left != 0) {
            dir = dir - 1;
        }
        if (right != 0) {
            dir = dir + 1;
        }
        // crouch (big hero only): stands up again only with headroom
        if (big != 0) {
            if (down != 0 && onGround != 0) {
                if (crouching == 0) {
                    crouching = 1;
                    double feet = y + h;
                    h = 22;
                    y = feet - h;
                }
            } else if (crouching != 0 && (down == 0 || onGround == 0)) {
                if (down == 0) {
                    int tx0 = tileOf(x + 0.01);
                    int tx1 = tileOf(x + w - 0.01);
                    int ty = tileOf(y + h - 32 - 0.01);
                    int blocked = 0;
                    int tx = tx0;
                    while (tx <= tx1) {
                        if (lv.solidAt(tx, ty) != 0) {
                            blocked = 1;
                        }
                        tx = tx + 1;
                    }
                    if (blocked == 0) {
                        crouching = 0;
                        double feet = y + h;
                        h = 32;
                        y = feet - h;
                    }
                }
            }
        }
        if (crouching != 0 && onGround != 0) {
            dir = 0;
        }

        double maxSpeed = MAX_WALK;
        double acc = WALK_ACC;
        if (run != 0) {
            maxSpeed = MAX_RUN;
            acc = RUN_ACC;
        }
        skidding = 0;
        if (dir != 0) {
            facing = dir;
            if (onGround != 0 && vx * dir < 0) {
                vx = vx + dir * SKID_DECEL;
                skidding = 1;
            } else if (onGround == 0 && vx * dir < 0) {
                vx = vx + dir * RUN_ACC;
            } else {
                if (Math.abs(vx) < maxSpeed) {
                    vx = vx + dir * acc;
                    if (Math.abs(vx) > maxSpeed) {
                        vx = dir * maxSpeed;
                    }
                } else if (onGround != 0) {
                    // slow back down to walking pace after a run
                    vx = vx - dir * FRICTION;
                    if (Math.abs(vx) < maxSpeed) {
                        vx = dir * maxSpeed;
                    }
                }
            }
        } else if (onGround != 0) {
            if (vx > 0) {
                vx = vx - FRICTION;
                if (vx < 0) {
                    vx = 0;
                }
            } else if (vx < 0) {
                vx = vx + FRICTION;
                if (vx > 0) {
                    vx = 0;
                }
            }
        }

        if (onGround != 0 && jumpPressed != 0) {
            double ax = Math.abs(vx);
            if (ax < 1.0) {
                vy = -4.0;
                gravityHold = 0.125;
                gravityFall = 0.4375;
            } else if (ax < 2.3125) {
                vy = -4.0;
                gravityHold = 0.1172;
                gravityFall = 0.375;
            } else {
                vy = -5.0;
                gravityHold = 0.15625;
                gravityFall = 0.5625;
            }
            jumping = 1;
            onGround = 0;
        }
        if (vy < 0 && jumpHeld != 0 && jumping != 0) {
            vy = vy + gravityHold;
        } else {
            vy = vy + gravityFall;
            jumping = 0;
        }
        if (vy > MAX_FALL) {
            vy = MAX_FALL;
        }
        step(lv, 1);
        if (onGround != 0) {
            jumping = 0;
            stompCombo = 0;
        }
        if (invincible > 0) {
            invincible = invincible - 1;
        }
        // walk animation clock follows speed
        double sp = Math.abs(vx);
        if (onGround != 0 && sp > 0.05) {
            walkTimer = walkTimer + 1;
            int period = 9;
            if (sp > 1.0) {
                period = 6;
            }
            if (sp > 2.0) {
                period = 4;
            }
            if (walkTimer >= period) {
                walkTimer = 0;
                walkFrame = walkFrame + 1;
                if (walkFrame >= 3) {
                    walkFrame = 0;
                }
            }
        }
    }
}

class SmEnemy extends SmBody {
    /** 0 goomba, 1 koopa (walking), 2 shell (still), 3 shell (sliding). */
    int kind;
    int active;
    int dir = -1;
    /** Frames left of the squash animation (goomba), 0 when not squashed. */
    int squash;
    int dead;
    int anim;
    int kickGrace;
    int removed;
    int combo;

    SmEnemy(int k, double px, double py) {
        kind = k;
        w = 14;
        h = 14;
        x = px + 1;
        y = py + 2;
    }

    void setKind(int k) {
        kind = k;
    }
}

class SmItem extends SmBody {
    /** 0 power-up mushroom, 1 block coin (pop), 2 score popup, 3 brick debris, 4 fire flower, 5 spark, 6 starman. */
    int kind;
    int life;
    int emerge;
    int value;
    int dir = 1;
    int frame;
    double startY;

    SmItem(int k, double px, double py) {
        kind = k;
        x = px;
        y = py;
        w = 12;
        h = 14;
    }
}

class SmFireball extends SmBody {
    int dir = 1;
    int anim;
    int dead;
    int removed;

    SmFireball(double px, double py, int d) {
        x = px;
        y = py;
        dir = d;
        w = 8;
        h = 8;
        vx = d * 3.5;
    }
}
