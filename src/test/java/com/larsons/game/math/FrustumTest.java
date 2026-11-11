package com.larsons.game.math;

import com.larsons.game.world.OrbitCamera;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** What the camera sees: nothing in view is ever said to be out of it, and what is well outside is. */
class FrustumTest {

    @Test
    void everythingOnScreenIsSeenAndWhatIsFarOffToTheSideIsNot() {
        Random r = new Random(7);
        int outside = 0, culled = 0;
        for (int view = 0; view < 40; view++) {
            OrbitCamera camera = new OrbitCamera(r.nextDouble() * Math.PI * 2, 0.1 + r.nextDouble() * 1.4,
                    2 + r.nextDouble() * 40);
            camera.snapTo(new Vec3(r.nextDouble() * 200 - 100, r.nextDouble() * 10, r.nextDouble() * 200 - 100));
            double aspect = 16 / 9.0;
            Mat4 vp = camera.viewProjection(aspect);
            Frustum f = new Frustum(vp);
            Vec3 eye = camera.eye(), fwd = camera.forward(), right = camera.right(), up = camera.up();
            double tan = Math.tan(camera.fovY() / 2);
            for (int i = 0; i < 400; i++) {
                // a point by where it is on the screen (-1 to 1 is on it) and how far off
                double sx = r.nextDouble() * 6 - 3, sy = r.nextDouble() * 6 - 3, depth = 0.5 + r.nextDouble() * 150;
                Vec3 p = eye.add(fwd.scale(depth)).add(right.scale(sx * depth * tan * aspect))
                        .add(up.scale(sy * depth * tan));
                boolean onScreen = Math.abs(sx) <= 1 && Math.abs(sy) <= 1;
                if (onScreen) {
                    assertTrue(f.sees(p, 0), "in view at " + sx + ", " + sy + ", " + depth + " m");
                    assertTrue(f.sees(p.x(), p.y(), p.z(), 3));
                } else {
                    outside++;
                    // well off the screen's edge, a small thing is not seen; a big enough one still is
                    double off = Math.max(Math.abs(sx), Math.abs(sy)) - 1;
                    if (off > 0.5 && !f.sees(p, 0.01)) culled++;
                    assertTrue(f.sees(p, 3.2 * depth * tan * aspect + 1), "a ball that reaches the screen");
                }
            }
            // behind her eye: not seen, unless it is big enough to reach round
            Vec3 behind = eye.sub(fwd.scale(20));
            assertFalse(f.sees(behind, 1));
            assertTrue(f.sees(behind, 25));
            // what she looks at is always in view
            assertTrue(f.sees(camera.target(), 0));
        }
        assertTrue(culled > outside * 0.6, culled + " of " + outside + " outside the view were left out");
    }
}
