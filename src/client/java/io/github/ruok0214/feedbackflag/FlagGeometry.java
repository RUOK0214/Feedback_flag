package io.github.ruok0214.feedbackflag;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Shared by rendering and picking so resized and moved flags remain clickable. */
record FlagGeometry(AABB pole, AABB cloth, AABB hitBox, Vec3 label) {
    static FlagGeometry of(FlagBook.Target target, VisualSettings settings) {
        var origin = new Vec3(target.x() + .5 + settings.offsetX(), target.y() + 1 + settings.offsetY(),
                target.z() + .5 + settings.offsetZ());
        double scale = settings.flagScale();
        return new FlagGeometry(box(origin, scale, -.02, 0, -.02, .02, .85, .02),
                box(origin, scale, .02, .48, -.02, .55, .82, .02),
                box(origin, scale, -.05, 0, -.07, .58, .85, .07), origin.add(0, 1.04 * scale, 0));
    }
    private static AABB box(Vec3 origin, double scale, double x1, double y1, double z1, double x2, double y2, double z2) {
        return new AABB(origin.x + x1 * scale, origin.y + y1 * scale, origin.z + z1 * scale,
                origin.x + x2 * scale, origin.y + y2 * scale, origin.z + z2 * scale);
    }
}
