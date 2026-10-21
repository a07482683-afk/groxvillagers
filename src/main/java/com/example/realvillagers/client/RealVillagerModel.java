package com.example.realvillagers.client;

import com.example.realvillagers.RealVillager;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;

public class RealVillagerModel extends HumanoidModel<RealVillager> {
    public RealVillagerModel(ModelPart root) { super(root); }

    @Override
    public void setupAnim(RealVillager e, float limbSwing, float limbAmount, float age, float headYaw, float headPitch) {
        crouching = e.isCrouching();
        ItemStack held = e.getMainHandItem();
        rightArmPose = held.isEmpty() ? ArmPose.EMPTY : ArmPose.ITEM;
        leftArmPose = ArmPose.EMPTY;
        if (e.isAggressive()) {
            if (held.getItem() instanceof BowItem) rightArmPose = ArmPose.BOW_AND_ARROW;
            else if (held.getItem() instanceof CrossbowItem) rightArmPose = ArmPose.CROSSBOW_HOLD;
            else if (held.getItem() instanceof TridentItem) rightArmPose = ArmPose.THROW_SPEAR;
        }
        super.setupAnim(e, limbSwing, limbAmount, age, headYaw, headPitch);

        float t = age * 0.3F;
        switch (e.getAnim()) {
            case RealVillager.ANIM_WAVE -> {          // машет рукой
                rightArm.xRot = -2.6F;
                rightArm.zRot = 0.3F + Mth.sin(age * 0.8F) * 0.4F;
            }
            case RealVillager.ANIM_DANCE -> {         // танцует
                rightArm.xRot = -2.8F + Mth.sin(t * 2) * 0.4F;
                leftArm.xRot = -2.8F + Mth.cos(t * 2) * 0.4F;
                rightArm.zRot = 0.4F;
                leftArm.zRot = -0.4F;
                head.zRot = Mth.sin(t) * 0.3F;
                rightLeg.xRot = Mth.sin(t * 2) * 0.6F;
                leftLeg.xRot = -Mth.sin(t * 2) * 0.6F;
            }
            case RealVillager.ANIM_HANDS_UP -> {      // сдаётся
                rightArm.xRot = -3.0F; rightArm.zRot = 0.2F;
                leftArm.xRot = -3.0F;  leftArm.zRot = -0.2F;
            }
            case RealVillager.ANIM_SAD -> {           // закрывает лицо руками
                head.xRot = 0.5F;
                rightArm.xRot = -2.2F; rightArm.yRot = -0.5F;
                leftArm.xRot = -2.2F;  leftArm.yRot = 0.5F;
            }
            case RealVillager.ANIM_POINT -> {         // показывает пальцем
                rightArm.xRot = -1.57F + head.xRot;
                rightArm.yRot = head.yRot;
            }
            default -> {}
        }
        hat.copyFrom(head);
    }
}
