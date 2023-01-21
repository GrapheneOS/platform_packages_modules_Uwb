/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.server.uwb.correction.math;

import static java.lang.Math.tan;
import static java.lang.Math.toRadians;

import androidx.annotation.NonNull;

/**
 * 4x4 Matrix representing translation, scale, and rotation. Column major, right handed:
 *
 * <pre>
 *  [0, 4,  8, 12]
 *  [1, 5,  9, 13]
 *  [2, 6, 10, 14]
 *  [3, 7, 11, 15]
 * </pre>
 */
public class Matrix {
    private static final float[] IDENTITY_DATA =
            new float[] {
                    1.0f, 0.0f, 0.0f, 0.0f,
                    0.0f, 1.0f, 0.0f, 0.0f,
                    0.0f, 0.0f, 1.0f, 0.0f,
                    0.0f, 0.0f, 0.0f, 1.0f
            };

    public final float[] data = new float[16];

    private Matrix() {}

    /**
     * Creates a new instance of the Matrix class.
     * @param data A 16-element array of the matrix contents.
     */
    public Matrix(float[] data) {
        set(data);
    }

    private void set(float[] data) {
        if (data.length != 16) {
            throw new IllegalArgumentException("Cannot set Matrix, invalid data.");
        }

        System.arraycopy(data, 0, this.data, 0, 16);
    }

    /**
     * Gets the identity matrix.
     *
     * @return The identity matrix.
     */
    public static Matrix identity() {
        return new Matrix(IDENTITY_DATA);
    }

    /** Transform a Vector3 with this Matrix. */
    public Vector3 transformPoint(Vector3 vector) {
        float vx = vector.x;
        float vy = vector.y;
        float vz = vector.z;

        float x, y, z;
        x = data[0] * vx;
        x += data[4] * vy;
        x += data[8] * vz;
        x += data[12]; // *1

        y = data[1] * vx;
        y += data[5] * vy;
        y += data[9] * vz;
        y += data[13]; // *1

        z = data[2] * vx;
        z += data[6] * vy;
        z += data[10] * vz;
        z += data[14]; // *1
        return new Vector3(x, y, z);
    }

    /** Multiply two Matrices together and store the result in a third Matrix. */
    public static void multiply(Matrix lhs, Matrix rhs, Matrix dest) {
        android.opengl.Matrix.multiplyMM(dest.data, 0, lhs.data, 0, rhs.data, 0);
    }

    /** Invert a Matrix and store the result in another Matrix. */
    public static boolean invert(Matrix matrix, Matrix dest) {
        return android.opengl.Matrix.invertM(dest.data, 0, matrix.data, 0);
    }

    /** Set the translation component of this Matrix. */
    public void setTranslation(Vector3 translation) {
        data[12] = translation.x;
        data[13] = translation.y;
        data[14] = translation.z;
    }

    /** Make this a translation Matrix. */
    public void makeTranslation(Vector3 translation) {
        set(IDENTITY_DATA);
        setTranslation(translation);
    }

    /** Make this a rotation Matrix. */
    public void makeRotation(Quaternion q) {
        set(IDENTITY_DATA);

        float tx = 2 * q.x;
        float ty = 2 * q.y;
        float tz = 2 * q.z;

        float mdsqx = 1 - tx * q.x;
        float dqxy = ty * q.x;
        float dsqy = ty * q.y;
        float dqyz = tz * q.y;
        float dsqz = tz * q.z;
        float dqxz = tx * q.z;
        float dqxw = tx * q.w;
        float dqyw = ty * q.w;
        float dqzw = tz * q.w;

        data[0] = 1 - dsqy - dsqz;
        data[4] = dqxy - dqzw;
        data[8] = dqxz + dqyw;

        data[1] = dqxy + dqzw;
        data[5] = mdsqx - dsqz;
        data[9] = dqyz - dqxw;

        data[2] = dqxz - dqyw;
        data[6] = dqyz + dqxw;
        data[10] = mdsqx - dsqy;
    }

    /** Make this a rigid transform Matrix. */
    public void makeRigidTransform(Pose pose) {
        makeRotation(pose.rotation);
        setTranslation(pose.translation);
    }

    /** Make this a uniform scale Matrix. */
    public void makeScale(float scale) {
        set(IDENTITY_DATA);

        data[0] = scale;
        data[5] = scale;
        data[10] = scale;
    }

    /** Make this a scale Matrix. */
    public void makeScale(Vector3 scale) {
        set(IDENTITY_DATA);

        data[0] = scale.x;
        data[5] = scale.y;
        data[10] = scale.z;
    }

    /**
     * Make this a translation, rotation and scale Matrix. See {@link
     * #newTrs(Vector3,Quaternion,Vector3 )}.
     */
    public void makeTrs(Vector3 translation, Quaternion q, Vector3 scale) {
        float tx = 2 * q.x;
        float ty = 2 * q.y;
        float tz = 2 * q.z;

        float mdsqx = 1 - tx * q.x;
        float dqxy = ty * q.x;
        float dsqy = ty * q.y;
        float dqyz = tz * q.y;
        float dsqz = tz * q.z;
        float dqxz = tx * q.z;
        float dqxw = tx * q.w;
        float dqyw = ty * q.w;
        float dqzw = tz * q.w;

        data[0] = (1 - dsqy - dsqz) * scale.x;
        data[4] = (dqxy - dqzw) * scale.y;
        data[8] = (dqxz + dqyw) * scale.z;

        data[1] = (dqxy + dqzw) * scale.x;
        data[5] = (mdsqx - dsqz) * scale.y;
        data[9] = (dqyz - dqxw) * scale.z;

        data[2] = (dqxz - dqyw) * scale.x;
        data[6] = (dqyz + dqxw) * scale.y;
        data[10] = (mdsqx - dsqy) * scale.z;

        data[12] = translation.x;
        data[13] = translation.y;
        data[14] = translation.z;

        data[3] = 0.0f;
        data[7] = 0.0f;
        data[11] = 0.0f;
        data[15] = 1.0f;
    }

    /** Make this an orthographic projection Matrix in terms of the six clip planes. */
    public void makeOrtho(float left, float right, float bottom, float top, float near, float far) {
        android.opengl.Matrix.orthoM(data, 0, left, right, bottom, top, near, far);
    }

    /** Make this a projection Matrix in terms of the six clip planes. */
    public void makeFrustum(float left,
            float right,
            float bottom,
            float top,
            float near,
            float far) {
        android.opengl.Matrix.frustumM(data, 0, left, right, bottom, top, near, far);
    }

    /**
     * Make this a projection Matrix in terms of a field of view angle, an aspect ratio, and z clip
     * planes.
     */
    public void makePerspective(float fovy, float aspect, float near, float far) {
        android.opengl.Matrix.perspectiveM(data, 0, fovy, aspect, near, far);
    }

    /**
     * Make this a projection Matrix in terms of a field of view angle, an aspect ratio, z clip
     * planes, and an offset.
     */
    public void makePerspective(
            float fovy, float aspect, float near, float far, float offsetx, float offsety) {
        float size = near * (float) tan(toRadians(fovy) / 2.0);
        float bottom = -size * (1 + offsety);
        float top = size * (1 - offsety);
        float left = -size * aspect * (1 + offsetx);
        float right = size * aspect * (1 - offsetx);
        android.opengl.Matrix.frustumM(data, 0, left, right, bottom, top, near, far);
    }

    /** Create a new translation Matrix. */
    public static Matrix newTranslation(Vector3 translation) {
        Matrix m = new Matrix();
        m.makeTranslation(translation);
        return m;
    }

    /** Create a new rotation Matrix. */
    public static Matrix newRotation(Quaternion rotation) {
        Matrix m = new Matrix();
        m.makeRotation(rotation);
        return m;
    }

    /** Create a new rigid transform Matrix. */
    public static Matrix newRigidTransform(Pose pose) {
        Matrix m = new Matrix();
        m.makeRigidTransform(pose);
        return m;
    }

    /** Create a new uniform scale Matrix. */
    public static Matrix newScale(float scale) {
        Matrix m = new Matrix();
        m.makeScale(scale);
        return m;
    }

    /** Create a new scale Matrix. */
    public static Matrix newScale(Vector3 scale) {
        Matrix m = new Matrix();
        m.makeScale(scale);
        return m;
    }

    /**
     * Creates a new translation, rotation and scale Matrix. The returned matrix is such that it
     * first scales objects, then rotates them, and finally translates them.
     *
     * <p>See
     * https://www.opengl-tutorial.org/beginners-tutorials/tutorial-3-matrices/#cumulating-transformations
     */
    public static Matrix newTrs(Vector3 translation, Quaternion rotation, Vector3 scale) {
        Matrix m = new Matrix();
        m.makeTrs(translation, rotation, scale);
        return m;
    }

    /** Creates a new orthographic projection Matrix in terms of the six clip planes. */
    public static Matrix newOrtho(
            float left, float right, float bottom, float top, float near, float far) {
        Matrix m = new Matrix();
        m.makeOrtho(left, right, bottom, top, near, far);
        return m;
    }

    /** Creates a new projection Matrix in terms of the six clip planes. */
    public static Matrix newFrustum(
            float left, float right, float bottom, float top, float near, float far) {
        Matrix m = new Matrix();
        m.makeFrustum(left, right, bottom, top, near, far);
        return m;
    }

    /**
     * Creates a new projection Matrix in terms of a field of view angle, an aspect ratio, and z
     * clip planes.
     */
    public static Matrix newPerspective(float fovy, float aspect, float near, float far) {
        Matrix m = new Matrix();
        m.makePerspective(fovy, aspect, near, far);
        return m;
    }

    /**
     * Make this a projection Matrix in terms of a field of view angle, an aspect ratio, z clip
     * planes, and an offset.
     */
    public static Matrix newPerspective(
            float fovy, float aspect, float near, float far, float offsetx, float offsety) {
        Matrix m = new Matrix();
        m.makePerspective(fovy, aspect, near, far, offsetx, offsety);
        return m;
    }

    @NonNull
    @Override
    public String toString() {
        return "Matrix:\n[ "
                + data[0]
                + "\t"
                + data[4]
                + "\t"
                + data[8]
                + "\t"
                + data[12]
                + "\n  "
                + data[1]
                + "\t"
                + data[5]
                + "\t"
                + data[9]
                + "\t"
                + data[13]
                + "\n  "
                + data[2]
                + "\t"
                + data[6]
                + "\t"
                + data[10]
                + "\t"
                + data[14]
                + "\n  "
                + data[3]
                + "\t"
                + data[7]
                + "\t"
                + data[11]
                + "\t"
                + data[15]
                + " ]";
    }
}
