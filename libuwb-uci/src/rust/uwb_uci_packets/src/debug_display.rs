use std::{fmt::Debug, ops::Deref, path::Display};

use crate::{ParsedFrameReport, PathSample, SegmentMetricsValue};

pub struct DebugOverride<T>(T);

impl<T> DebugOverride<T> {
    fn take(self) -> T {
        self.0
    }
}

impl<T> From<T> for DebugOverride<T> {
    fn from(value: T) -> Self {
        DebugOverride::<T>(value)
    }
}

impl<T> Deref for DebugOverride<T> {
    type Target = T;

    fn deref(&self) -> &T {
        &self.0
    }
}

impl Debug for ParsedFrameReport {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("FrameReport")
            .field("uwb_msg_id", &self.uwb_msg_id)
            .field("action", &self.action)
            .field("antenna_set", &self.antenna_set)
            .field("rssi", &self.rssi)
            .field("aoa", &self.aoa)
            .field("cir", &self.cir)
            .field("segment_metrics", &self.segment_metrics.iter().map(DebugOverride))
            .finish()
    }
}

impl Debug for DebugOverride<Vec<SegmentMetricsValue>> {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_tuple("").field(&self.0).finish()
    }
}

impl Debug for DebugOverride<&SegmentMetricsValue> {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("")
            .field("receiver/segment", &self.0.receiver_and_segment)
            .field("rf_noise_floor", &f32::from(QFormat::<8, 8>(self.0.rf_noise_floor)))
            .field("segment_rsl", &f32::from(QFormat::<8, 8>(self.0.segment_rsl)))
            .field("first_path", &DebugOverride(&self.0.first_path))
            .field("peak_path", &DebugOverride(&self.0.peak_path))
            .finish()
    }
}

impl Debug for DebugOverride<&PathSample> {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("")
            .field("index", &self.0.index)
            .field("rsl", &f32::from(QFormat::<8, 8>(self.0.rsl)))
            .field("time_ns", &f32::from(QFormat::<6, 9>(self.0.time_ns)))
            .finish()
    }
}

#[derive(Copy, Clone)]
pub struct QFormat<const I: u8, const F: u8>(u16);

impl<const I: u8, const F: u8> From<QFormat<I, F>> for f32 {
    fn from(value: QFormat<I, F>) -> Self {
        let int_part = (value.0 >> F);
        let frac_mask = (1 << F) - 1;
        let frac_part = value.0 & frac_mask;
        let frac = 2.0_f32.powf(-f32::from(F)) * f32::from(frac_part);
        f32::from(int_part) + frac
    }
}
