/*
 * Copyright (c) 2018.
 * João Paulo Sena <joaopaulo761@gmail.com>
 *
 * This file is part of the UNES Open Source Project.
 *
 * UNES is licensed under the MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.forcetower.uefs.core.storage.repository

import android.content.Context
import com.forcetower.sagres.database.model.SPerson
import com.forcetower.uefs.AppExecutors
import com.forcetower.uefs.R
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.auth.FirebaseAuth
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val context: Context,
    private val executors: AppExecutors
){
    private val secret = context.getString(R.string.firebase_account_secret)

    fun loginToFirebase(person: SPerson) {
        attemptSignIn(person.email.trim(), "!!_${person.cpf.trim()}_##_${person.id}_**_$secret")
    }

    private fun attemptSignIn(email: String, password: String) {
        Timber.d("Attempt Login")
        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(executors.others(), OnCompleteListener {task ->
                    if (task.isSuccessful) {
                        if (firebaseAuth.currentUser == null) {
                            attemptCreateAccount(email, password)
                        } else {
                            Timber.d("Connected! Your account is: ${firebaseAuth.currentUser}")
                        }
                    } else {
                        Timber.d("Failed to Sign In...")
                        Timber.d("Exception: ${task.exception}")
                        attemptCreateAccount(email, password)
                    }
                })
    }

    private fun attemptCreateAccount(email: String, password: String) {
        Timber.d("Attempt Create account")
        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(executors.others(), OnCompleteListener {task ->
                    if (task.isSuccessful) {
                        if (firebaseAuth.currentUser == null) {
                            Timber.d("Failed anyways")
                        } else {
                            Timber.d("Connected! Your account is: ${firebaseAuth.currentUser}")
                        }
                    } else {
                        Timber.d("Failed to Create account...")
                        Timber.d("Exception: ${task.exception}")
                    }
                })
    }
}