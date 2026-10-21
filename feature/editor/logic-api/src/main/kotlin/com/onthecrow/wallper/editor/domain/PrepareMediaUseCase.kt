package com.onthecrow.wallper.editor.domain

import com.onthecrow.wallper.editor.domain.model.PreparedMedia

/** Copies the picked media into scratch storage, makes its thumbnail and reads its size and rotation. */
interface PrepareMediaUseCase {
    suspend operator fun invoke(uri: String): Result<PreparedMedia>
}
